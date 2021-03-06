/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.ring.internal.metadata;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.solr.common.SolrInputDocument;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.ring.XWikiRingIndexer;
import org.xwiki.contrib.ring.internal.services.SolrRingIndexer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.search.solr.internal.api.SolrIndexerException;
import org.xwiki.search.solr.internal.metadata.DocumentSolrMetadataExtractor;
import org.xwiki.search.solr.internal.metadata.LengthSolrInputDocument;

/**
 * Overrides DocumentSolrMetadataExtractor in order create SolrInputDocuments not from scratch but from the index, so
 * that if there is any existing indexed data that should not be replaced by the extractor, it should be left as is.
 * Typically, the XWikiRingSolrMetadataExtractor adds some values to the index of a document (e.g. the destination of an
 * ringSet), that should remain present when the document metadata extraction occurs: this extraction should remove all
 * keys that do not relate to RRing keys, but keep the RRing key/value pairs.
 *
 * A higher priority is set in components.txt so that this component gets loaded instead of
 * DocumentSolrMetadataExtractor.
 *
 * @version $Id$
 */
@Component
@Named("document")
@Singleton
public class XWikiTermSolrMetadataExtractor extends DocumentSolrMetadataExtractor
{
    @Inject
    @Named("solr")
    private XWikiRingIndexer indexer;

    public LengthSolrInputDocument getSolrDocument(EntityReference entityReference)
            throws SolrIndexerException, IllegalArgumentException
    {
        try {
            LengthSolrInputDocument solrDocument =
                    ((SolrRingIndexer) indexer).getSolrInputDocument(entityReference, false);

            // TODO: remove all fields that do not relate to ringSet relations, if needed

            // No need to set document fields since it's already performed in ringSet.getSolrInputDocument
//            if (!setDocumentFields(new DocumentReference(entityReference.extractReference(EntityType.DOCUMENT)),
//                    solrDocument))
//            {
//                return null;
//            }

            if (!setFieldsInternal(solrDocument, entityReference)) {
                return null;
            }

            return solrDocument;
        } catch (Exception e) {
            String message = String.format("Failed to get input Solr document for entity '%s'", entityReference);
            throw new SolrIndexerException(message, e);
        }
    }

    /**
     * Needed to set document fields from DefaultXWikiRRing. TODO: see if {@link org.xwiki.search.solr.internal.metadata.AbstractSolrMetadataExtractor}
     * setDocumentFields can be made public instead or if the field initializer can be moved to a class that is more
     * accessible.
     */
    public boolean setDocumentFieldsPublic(DocumentReference documentReference, SolrInputDocument solrDocument)
            throws Exception
    {
        return super.setDocumentFields(documentReference, solrDocument);
    }


        /*

        Add Ring
        String field = "property." + ringSet.serialize(ringSet.getRelation());
        List<DocumentReference> destinations = new ArrayList<>();
        String typeVertexIdentifierAsString =
                ringSet.serialize(ringSet.getIdentifier((DefaultXWikiRRing.TYPE_TERM_NAME)));
        if (ringSet.hasRelatum()) {
            addFieldValueOnce(solrDocument, FieldUtils.getFieldName(field, TypedValue.STRING, locale),
                    ringSet.serialize(ringSet.getRelatum()));

            // Currently needed for displaying all neighbours of a vertex with a single query
            field =
                    "property." + ringSet.serialize(ringSet.getIdentifier(DefaultXWikiRRing.IS_CONNECTED_TO_RELATION_NAME));
            addFieldValueOnce(solrDocument, FieldUtils.getFieldName(field, TypedValue.STRING, locale),
                    ringSet.serialize(ringSet.getRelatum()));


            //destinations.index(ringSet.getRelatum());

            // Index rings that exist by transitivity, except Type, which is special, because we don't
            // want that "A is a Type" when "A is a B" and "B is a Type". We want "A is a Type" only if
            // explicitely set. Otherwise we need to introduce a specific relation "is instance of" which
            // differs from "is a".
                XWikiRelation xrelation = ringSet.getRelation(ringSet.getRelation());
                if (xrelation.isTransitive()) {
                    List<XWikiRing> ringsByTransitivity =
                            ringSet.getRingsFrom(ringSet.getRelatum(), ringSet.getRelation());
                    for (XWikiRing ringByTransitivity : ringsByTransitivity) {
                        // We need to compare local serialized references because the full references may differ
                        // due to different wiki context (this extractor is executed in the context of the main wiki)
                        String localDestinationIdentifierAsString =
                                ringSet.serialize(ringByTransitivity.getRelatum());
                        if (!localDestinationIdentifierAsString.equals(typeVertexIdentifierAsString)) {
                            addFieldValueOnce(solrDocument, FieldUtils.getFieldName(field, TypedValue.STRING, locale),
                                    ringSet.serialize(ringByTransitivity.getRelatum()));
                            destinations.index(ringByTransitivity.getRelatum());
                        }
                    }
                }

            //field = "property." + ringSet.serialize(DefaultXWikiRRing.ring_VERTEX_REFERENCE) + "."
            //       + DefaultXWikiRRing.HAS_ORIGIN_ID;
            //for (DocumentReference destination : destinations) {
//            addFieldValueOnce(solrDocument, FieldUtils.getFieldName(field, TypedValue.STRING, locale),
//                    ringSet.serialize(ringSet.getReferent()));
            //}

        } else if (ringSet.hasValue()) {
            // TODO: handle cases where the value is not a string
            solrDocument.addField(FieldUtils.getFieldName(field, TypedValue.STRING, locale), ringSet.getValue());
        }
        */

    /**
     * The method computes all the documents that have the current document as a ringSet destination, and it adds them to
     * the indexed fields. TODO: see if we need to check that the relation is not empty. When a document is modified, we
     * need to reindex all the documents that have this document as destination. TODO: optimize this to the minimum set
     * of operations.
     */
    /*
    protected void addInverserings(SolrInputDocument solrDocument, DocumentReference vertex,
            Locale locale) throws RingException
    {

        // - Below, we make sure that the vertex has a reverse index for all the vertices it's a destination of.
        // - ? Trigger a reindex of the subject's statements objects (this should not be fired from here but
        // from an XObjectAdded / Deleted / PropertyUpdated event

        logger.debug("Add inverse rings of {}", vertex);
        List<DocumentReference> directPredecessors = ringSet.getDirectPredecessorsViaHql(vertex);
        logger.debug("Direct predecessors of {}: {}", vertex, directPredecessors);
        for (DocumentReference directPredecessor : directPredecessors) {
            // If the predecessor is the vertex itself, don't index an inverse ringSet since the ringSet links the vertex to
            // itself.
            if (!directPredecessor.equals(vertex)) {
                String field =
                        "property." + ringSet.serialize(ringSet.getIdentifier(DefaultXWikiRRing.IS_CONNECTED_TO_RELATION_NAME));
                addFieldValueOnce(solrDocument, FieldUtils.getFieldName(field, TypedValue.STRING, locale),
                        ringSet.serialize(directPredecessor));

                // If the predecessor has some direct predecessors (via HQL as well) with transitive relations,
                // then index these rings destinations as well.
//                List<DocumentReference> directPredecessorPredecessors =
//                        ringSet.getDirectPredecessorsViaHql(directPredecessor,
//                                ringSet.getIdentifier(DefaultXWikiRRing.IS_A_RELATION_NAME));
//                for (DocumentReference directPredecessorPredecessor : directPredecessorPredecessors) {
//                    if (!directPredecessorPredecessor.equals(directPredecessor)) {
//                        addFieldValueOnce(solrDocument, FieldUtils.getFieldName(field, TypedValue.STRING, locale),
//                                ringSet.serialize(directPredecessorPredecessor));
//                    }
//                }
            }
        }
    }
    */
}