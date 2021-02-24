/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.xwiki.contrib.graph;

import org.xwiki.component.annotation.Role;
import org.xwiki.hypergraph.Edge;
import org.xwiki.hypergraph.GraphException;
import org.xwiki.hypergraph.GraphIndexer;
import org.xwiki.model.reference.DocumentReference;

@Role
public interface XWikiGraphIndexer extends GraphIndexer<DocumentReference>
{

    void index(Edge<DocumentReference> edge) throws GraphException;

    /**
     * Remove edge from index
     */
    void unindex(Edge<DocumentReference> edge) throws GraphException;
}