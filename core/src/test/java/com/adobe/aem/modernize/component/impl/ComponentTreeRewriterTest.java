package com.adobe.aem.modernize.component.impl;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import mockit.Expectations;
import mockit.Mocked;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ComponentTreeRewriterTest {

  // Oak needed to verify order preservation.
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  @Mocked
  private RewriteRule simpleRule;


  @Test
  public void preservesOrder() throws Exception {

    List<RewriteRule> rules = new ArrayList<>();
    rules.add(simpleRule);

    new Expectations() {{
      simpleRule.matches(withInstanceOf(Node.class));
      result = false;
      times = 9;
    }};


    context.load().json("/rewrite/test-ordered.json", "/content/test");
    Node root = context.resourceResolver().getResource("/content/test/ordered").adaptTo(Node.class);
    ComponentTreeRewriter.process(root, rules);

    Session session = root.getSession();
    assertTrue(session.hasPendingChanges(), "Updates were made");
    session.save();
    Resource updated = context.resourceResolver().getResource("/content/test/ordered");

    // Preserved Order
    Iterator<Resource> children = updated.listChildren();
    assertEquals("simple", children.next().getName(), "First child correct.");
    assertEquals("mapProperties", children.next().getName(), "Second child correct.");
    assertEquals("rewriteProperties", children.next().getName(), "Third child correct.");
    assertEquals("rewriteMapChildren", children.next().getName(), "Fourth child correct.");
  }

  @Test
  public void skipsFinalPaths() throws Exception {

    SetRootFinalRewriteRule finalRewriteRule = new SetRootFinalRewriteRule("/content/test/final/mapProperties");
    List<RewriteRule> rules = new ArrayList<>();
    rules.add(simpleRule);
    rules.add(finalRewriteRule);

    new Expectations() {{
      simpleRule.matches(withInstanceOf(Node.class));
      result = false;
    }};

    context.load().json("/rewrite/test-final.json", "/content/test");
    Node root = context.resourceResolver().getResource("/content/test/final").adaptTo(Node.class);
    ComponentTreeRewriter.process(root, rules);

    Session session = root.getSession();
    assertTrue(session.hasPendingChanges(), "Updates were made");
    session.save();

    // Should only be called once when matched.
    assertEquals(1, finalRewriteRule.invoked, "Rewrite rule invocations");
  }

  @Test
  void infiniteLoop() throws Exception {

    NoOpRewriteRule rule = new NoOpRewriteRule();
    List<RewriteRule> rules = new ArrayList<>();
    rules.add(rule);

    context.load().json("/rewrite/test-ordered.json", "/content/test");
    Node root = context.resourceResolver().getResource("/content/test/ordered").adaptTo(Node.class);
    ComponentTreeRewriter.process(root, rules);

    Session session = root.getSession();
    assertTrue(session.hasPendingChanges(), "Updates were made");
    session.save();

    assertEquals(9, rule.invoked, "Rewrite rule invocations");
  }

  private static final class SetRootFinalRewriteRule implements RewriteRule {

    private final String path;
    public int invoked = 0;
    public SetRootFinalRewriteRule(String path) {
      this.path = path;
    }

    @Override
    public String getId() {
      return "Mock";
    }

    @Override
    public boolean matches(@NotNull Node root) throws RepositoryException {
      if (StringUtils.equals(root.getPath(), path)) {
        invoked++;
      }
      return StringUtils.equals(root.getPath(), path);
    }

    @Override
    public Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RepositoryException {
      finalPaths.add(root.getPath());
      return root;
    }
  }

  private static final class NoOpRewriteRule implements RewriteRule {

    public int invoked = 0;
    @Override
    public String getId() {
      return NoOpRewriteRule.class.getName();
    }

    @Override
    public boolean matches(@NotNull Node root) throws RepositoryException {
      return true;
    }

    @Override
    public @Nullable Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RewriteException, RepositoryException {
      invoked++;
      return root;
    }
  }
}
