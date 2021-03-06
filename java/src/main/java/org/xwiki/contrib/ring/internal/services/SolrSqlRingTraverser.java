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
package org.xwiki.contrib.ring.internal.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.ring.XWikiRelation;
import org.xwiki.contrib.ring.XWikiRing;
import org.xwiki.contrib.ring.XWikiRingIndexer;
import org.xwiki.contrib.ring.XWikiRingTraverser;
import org.xwiki.contrib.ring.XWikiRingFactory;
import org.xwiki.contrib.ring.internal.model.DefaultXWikiRing;
import org.xwiki.contrib.ring.internal.model.Names;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.search.solr.internal.api.FieldUtils;
import org.xwiki.search.solr.internal.metadata.LengthSolrInputDocument;
import org.xwiki.text.StringUtils;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import aek.ring.Relation;
import aek.ring.RingException;
import aek.ring.TermSet;

@Component
@Singleton
@Named("solr-sql")
public class SolrSqlRingTraverser implements XWikiRingTraverser
{
    public final static int MAX = 100000;

    public static final String DEFAULT_SORT = FieldUtils.TITLE_SORT + " asc";

    @Inject
    @Named("solr")
    private XWikiRingIndexer indexer;

    @Inject
    private QueryManager querier;

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextualizer;

    @Inject
    private EntityReferenceResolver<SolrDocument> solrResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private XWikiRingFactory factory;

    protected Query createQuery(String solrQueryStatement, String sort, int max) throws RingException
    {
        try {
            Query query = querier.createQuery(solrQueryStatement, "solr");
            if (max == 0) {
                max = MAX;
            }
            query.setLimit(max);
            if (!StringUtils.isEmpty(sort)) {
                query.bindValue("sort", sort);
            } else {
                query.bindValue("sort", "score desc");
            }
            return query;
        } catch (QueryException e) {
            logger.error("createQuery {}", solrQueryStatement, e);
            throw new RingException(e);
        }
    }

    public List<XWikiRelation> filterRelations(DocumentReference term, List<? extends Relation<DocumentReference>> relations) throws RingException
    {
        List<XWikiRelation> compatibleRelations = new ArrayList<>();
        LengthSolrInputDocument vertexSolr = getSolrInputDocument(term);
        for (Relation<DocumentReference> relation : relations) {
            String domain = relation.getDomain();
            if (!StringUtils.isEmpty(domain)) {
                if (domain.equals(TermSet.ANY.getLabel())) {
                    compatibleRelations.add((XWikiRelation) relation);
                } else {
                    int idx = domain.indexOf(":");
                    if (idx > 0) {
                        String fieldName = domain.substring(0, idx);
                        String fieldValue = domain.substring(idx + 1).replaceAll("\"", "");
                        //TODO: do not append "string" manually
                        Collection<Object> values = vertexSolr.getFieldValues(fieldName + "_string");
                        if (values != null && values.contains(fieldValue)) {
                            compatibleRelations.add((XWikiRelation) relation);
                        }
                    }
                }
            } else {
                // if the applies-to constraint is empty, consider that the relation applies to any vertex
                compatibleRelations.add((XWikiRelation) relation);
            }
        }
        return compatibleRelations;
    }

    public List<DocumentReference> getDirectPredecessorsViaHql(DocumentReference term) throws RingException
    {
        try {
            logger.debug("Get SQL direct predecessors: {}", term);
            // NB: an HQL query is used here, not Solr QL, because this method is called by the Solr indexer to
            // compute the index
            // NB: no access right is checked here, because this method is meant to get used internally only.
            List<Object[]> entries = runRingQueryHql(Names.HAS_RELATUM, serializer.serialize(term), term.getWikiReference().getName());
            List<DocumentReference> referents = new ArrayList<>();
            for (Object[] entry : entries) {
                DocumentReference reference = resolver.resolve(entry[0].toString(), term);
                referents.add(reference);
            }
            //logger.debug("Direct predecessors of {} (HQL): ", vertex, vertices);
            return referents;
        } catch (QueryException e) {
            logger.error("Exception while getting direct predecessors of " + term, e);
            throw new RingException(e);
        }
    }

    public List<DocumentReference> getDirectPredecessorsViaHql(DocumentReference vertex, DocumentReference relation)
            throws RingException
    {
        try {
            logger.debug("Get SQL direct predecessors: {} {}", vertex, relation);
            // NB: an HQL query is used here, not Solr QL, because this method is called by the Solr indexer to
            // compute the index
            // NB: no access right is checked here, because this method is meant to get used internally only.
            Query query = this.querier.createQuery(
                    "select distinct obj.name, obj.number from BaseObject as obj, StringProperty as hasRelation,"
                            + "StringProperty as hasRelatum where obj.className = :className and "
                            + "hasRelation.id.id = obj.id and hasRelation.id.name = :hasRelation and "
                            + "hasRelatum.id.id = obj.id and hasRelatum.id.name = :hasRelatum and "
                            + "hasRelation.value  = :relation and hasRelatum.value = :destination", Query.HQL);
            query = query.bindValue("className", DefaultXWikiRing.RING_TERM_ID)
                    .bindValue("hasRelation", Names.HAS_RELATION)
                    .bindValue("hasRelatum", Names.HAS_RELATUM)
                    .bindValue("relation", serializer.serialize(relation))
                    .bindValue("destination", serializer.serialize(vertex));
            query.setWiki(vertex.getWikiReference().getName());
            List<Object[]> entries = query.execute();
            List<DocumentReference> vertices = new ArrayList<>();
            for (Object[] entry : entries) {
                DocumentReference reference = resolver.resolve(entry[0].toString(), vertex);
                vertices.add(reference);
            }
            return vertices;
        } catch (QueryException e) {
            logger.error("Exception while getting direct predecessors of " + vertex, e);
            throw new RingException(e);
        }
    }

    public XWikiRing getFirstRingFrom(DocumentReference referent, DocumentReference relation) throws RingException
    {
        for (XWikiRing ring : getRingsFrom(referent, relation)) {
            return ring;
        }
        return null;
    }

    public List<DocumentReference> getNeighbours(DocumentReference vertex) throws RingException
    {
        // TODO: escape dots?
        // TODO: we may need to escape anti-slash: #set ($subjectId = $subjectId.replaceAll('\\', '\\\\'))
        return run(getNeighboursQuery(vertex));
    }

    public Query getNeighboursQuery(DocumentReference vertex) throws RingException
    {
        String statement =
                SolrRingIndexer.PROPERTY_GRAPH_PREFIX +
                        serializer.serialize(factory.getIdentifier(Names.IS_CONNECTED_TO_RELATION_NAME)) + ":\"" +
                        serializer.serialize(vertex) + "\"";
        return createQuery(statement, DEFAULT_SORT, MAX);
    }

    // TODO: in theory, this should return all rings from vertex to destination and inversely, to be
    //  in symetry with removeRings(origin, destination)
    public List<XWikiRing> getRings(DocumentReference referent, DocumentReference relatum) throws RingException
    {
        // TODO: use functional interface to share common code with getRing()
        List<XWikiRing> rings = new ArrayList<>();
        XWikiDocument page = factory.getDocument(referent, false);
        for (Triple<EntityReference, Class, Class> ringType : factory.getRingClasses()) {
            for (BaseObject baseObject : page.getXObjects(ringType.getLeft())) {
                if (baseObject != null) {
                    XWikiRing ring = factory.createRing(baseObject);
                    if (relatum.equals(ring.getRelatum())) {
                        rings.add(ring);
                    }
                }
            }
        }
        return rings;
    }

    public List<XWikiRing> getRingsFrom(DocumentReference referent) throws RingException
    {
        return getRingsFrom(factory.getDocument(referent, false));
    }

    public List<XWikiRing> getRingsFrom(DocumentReference referent, DocumentReference relation) throws RingException
    {
        return getRingsFrom(factory.getDocument(referent, false), relation);
    }

    public List<XWikiRing> getRingsFrom(XWikiDocument page) throws RingException
    {
        // TODO: share code with #getRingsFrom(XWikiDocument, DocumentReference)
        List<XWikiRing> rings = new ArrayList<>();
        for (Triple<EntityReference, Class, Class> ringType : factory.getRingClasses()) {
            for (BaseObject baseObject : page.getXObjects(ringType.getLeft())) {
                if (baseObject != null) {
                    rings.add(factory.createRing(baseObject));
                }
            }
        }
        return rings;
    }

    // TODO: use optional single parameter instead of a vargs?
    public List<XWikiRing> getRingsFrom(XWikiDocument page, DocumentReference relation) throws RingException
    {
        List<XWikiRing> rings = new ArrayList<>();
        if (relation == null) {
            return rings;
        }
        for (Triple<EntityReference, Class, Class> ringType : factory.getRingClasses()) {
            for (BaseObject baseObject : page.getXObjects(ringType.getLeft())) {
                if (baseObject != null) {
                    // Add ringSet only if its relation matches with the one given as parameter.
                    if (serializer.serialize(relation).equals(baseObject.getStringValue(Names.HAS_RELATION))) {
                        rings.add(factory.createRing(baseObject));
                    }
                }
            }
        }
        return rings;
    }

    public List<XWikiRing> getRingsTo(DocumentReference relatum, DocumentReference relation)
            throws RingException
    {
        // TODO: escape dots in relation name? replaceAll("\\.", "..");
        // TODO: also escape slashes? replace("\\", "\\\\")?
        String statement =
                SolrRingIndexer.PROPERTY_GRAPH_PREFIX + serializer.serialize(relation) + ":\"" + serializer
                        .serialize(relatum)
                        + "\"";
        List<DocumentReference> referents =
                search(statement, SolrSqlRingTraverser.DEFAULT_SORT, SolrSqlRingTraverser.MAX);
        List<XWikiRing> rings = new ArrayList<>();
        for (DocumentReference referent : referents) {
            rings.add(factory.createRing(referent, relation, relatum));
        }
        return rings;
    }

    public LengthSolrInputDocument getSolrInputDocument(DocumentReference identifier) throws RingException
    {
        return ((SolrRingIndexer) indexer).getSolrInputDocument(identifier, true);
    }

    public List<DocumentReference> run(Query query) throws RingException
    {
        SolrDocumentList results = null;
        try {
            List<Object> searchResponse = query.execute();
            if (searchResponse != null && searchResponse.size() > 0) {
                QueryResponse response = (QueryResponse) searchResponse.get(0);
                results = response.getResults();
            }

            List<DocumentReference> vertices = new ArrayList<>();
            if (results == null) {
                return vertices;
            }
            for (SolrDocument result : results) {
                EntityType type = EntityType.valueOf((String) result.get(FieldUtils.TYPE));
                EntityReference reference = solrResolver.resolve(result, type);
                // TODO: only documents are expected, we may enforce this
                // TODO: uniqueness should be handled at the query level directly
                if (!vertices.contains(reference)) {
                    // We consider that all DocumentReferences can be retrieved, it's only the access to the
                    // referenced page itself that will get denied, otherwise this can raise issues with
                    // Solr pagination.
                    // authorizer.checkAccess(Right.VIEW, context.getUserReference(), reference);
                    // TODO: check it's ok to create a DocumentReference like this in all cases
                    vertices.add(new DocumentReference(reference));
                }
            }
            return vertices;
        } catch (QueryException e) {
            logger.error("Exception while running query: {}", query, e);
            throw new RingException(e);
        }
    }

    public List<Object[]> runRingQueryHql(String property, String destination, String wiki) throws QueryException
    {
        Query query = this.querier.createQuery(
                "select distinct obj.name, obj.number from BaseObject as obj, StringProperty as prop where "
                        + "obj.className = :className and prop.id.id = obj.id and prop.id.name = :property and "
                        + "prop.value = :destination", Query.HQL);
        query = query.bindValue("className", DefaultXWikiRing.RING_TERM_ID).bindValue("property", property)
                .bindValue("destination", destination);
        query.setWiki(wiki);
        return query.execute();
    }

    public List<DocumentReference> search(String text, Relation<DocumentReference> relation) throws RingException
    {
        String image = relation.getImage();
        String sql = "\"" + text + "\"";
        if (!StringUtils.isEmpty(image) && !image.equals(TermSet.ANY.getLabel())) {
            sql += " AND " + image;
        }
        String sort = "score desc";
        // If input is empty, return results sorted alphabetically
        if (StringUtils.isEmpty(text)) {
            sort = "title_sort asc";
        }
        return search(sql, sort, 30);
    }

    public List<DocumentReference> search(String text) throws RingException
    {
        String sql = "\"" + text + "\"";
        return search(sql, "score desc", 30);
    }

    public List<DocumentReference> search(String sql, String sort, int max) throws RingException
    {
        XWikiContext context = contextualizer.get();
        String wikiId = context.getWikiId();
        sql = "wiki:\"" + wikiId + "\"  AND type:DOCUMENT AND " + sql;
        Query query = createQuery(sql, sort, max);
        return run(query);
    }
}
