/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.graph;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 *  @author dsl
 */
public class GraphGenerator<Node> implements Graph <Node>{
  private final SemiGraph<Node> myGraph;

  public interface SemiGraph<Node> {
    Collection<Node> getNodes();
    Iterator<Node> getIn(Node n);
  }

  private final com.intellij.util.containers.HashMap<Node, Set<Node>> myOuts;

  public GraphGenerator(SemiGraph<Node> graph) {
    myGraph = graph;
    myOuts = new com.intellij.util.containers.HashMap<Node, Set<Node>>();
    buildOuts();
  }

  public static <T> GraphGenerator<T> create(SemiGraph<T> graph) {
    return new GraphGenerator<T>(graph);
  }

  private void buildOuts() {
    Collection<Node> nodes = myGraph.getNodes();
    for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
      Node node = iterator.next();
      myOuts.put(node, new HashSet<Node>());
    }

    for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
      Node node = iterator.next();
      Iterator<Node> inIt = myGraph.getIn(node);
      while (inIt.hasNext()) {
        Node inNode = inIt.next();
        myOuts.get(inNode).add(node);
      }
    }
  }

  public Collection<Node> getNodes() {
    return myGraph.getNodes();
  }

  public Iterator<Node> getIn(Node n) {
    return myGraph.getIn(n);
  }

  public Iterator<Node> getOut(Node n) {
    return myOuts.get(n).iterator();
  }

  public boolean hasArc(final Node from, final Node to) {
    return myOuts.get(from).contains(to);
  }
}
