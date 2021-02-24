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
package org.xwiki.contrib.graph.internal.model;

import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.objects.BaseObject;

public class BooleanXWikiEdge extends DefaultXWikiEdge
{
    public final static EntityReference XCLASS_REFERENCE =
            new EntityReference("BooleanEdgeClass", EntityType.DOCUMENT, Names.GRAPH_CODE_SPACE_REFERENCE);

    public BooleanXWikiEdge(BaseObject object, EntityReferenceSerializer<String> serializer,
            DocumentReferenceResolver<String> resolver)
    {
        super(object, serializer, resolver);
    }

    public Boolean getValue()
    {

        int value = object.getIntValue(Names.HAS_VALUE);
        if (value == 1) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public void setValue(Object value)
    {
        if (value == null) {
            return;
        }
        if (value != null && value instanceof Integer && ((Integer) value).intValue() == 1) {
            object.setIntValue(Names.HAS_VALUE, 1);
        }
        object.setIntValue(Names.HAS_VALUE, 0);
    }
}