package org.example;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.core.*;
import org.janusgraph.core.attribute.Geoshape;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.util.system.ConfigurationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestJanusGraphFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestJanusGraphFactory.class);

    protected JanusGraph graph;
    protected JanusGraphManagement management;
    protected GraphTraversalSource g;
    protected boolean supportsTransactions;
    protected boolean supportsGeoshape;
    protected String propFileName;

    public TestJanusGraphFactory(final String fileName) {
        propFileName = fileName;
        supportsGeoshape = true;
        supportsTransactions = true;
    }

    public void openGraph() throws ConfigurationException {
        LOGGER.info("opening graph");
        Configuration conf = ConfigurationUtil.loadPropertiesConfig(propFileName);
        graph = JanusGraphFactory.open(conf);
        management = graph.openManagement();
        g = graph.traversal();
    }

    public void createSchema() {
        try {
            // naive check if the schema was previously created
            if (management.getRelationTypes(RelationType.class).iterator().hasNext()) {
                management.rollback();
                return;
            }
            LOGGER.info("creating schema");
            createProperties(management);
            createVertexLabels(management);
            createEdgeLabels(management);
            createCompositeIndexes(management);
            management.commit();
        } catch (Exception e) {
            management.rollback();
        }
    }

    /**
     * Creates the vertex labels.
     */
    protected void createVertexLabels(final JanusGraphManagement management) {
        management.makeVertexLabel("titan").make();
        management.makeVertexLabel("location").make();
        management.makeVertexLabel("god").make();
        management.makeVertexLabel("demigod").make();
        management.makeVertexLabel("human").make();
        management.makeVertexLabel("monster").make();
    }

    /**
     * Creates the edge labels.
     */
    protected void createEdgeLabels(final JanusGraphManagement management) {
        management.makeEdgeLabel("father").multiplicity(Multiplicity.MANY2ONE).make();
        management.makeEdgeLabel("mother").multiplicity(Multiplicity.MANY2ONE).make();
        management.makeEdgeLabel("lives").signature(management.getPropertyKey("reason")).make();
        management.makeEdgeLabel("pet").make();
        management.makeEdgeLabel("brother").make();
        management.makeEdgeLabel("battled").make();
    }

    /**
     * Creates the properties for vertices, edges, and meta-properties.
     */
    protected void createProperties(final JanusGraphManagement management) {
        management.makePropertyKey("name").dataType(String.class).make();
        management.makePropertyKey("age").dataType(Integer.class).make();
        management.makePropertyKey("time").dataType(Integer.class).make();
        management.makePropertyKey("reason").dataType(String.class).make();
        management.makePropertyKey("place").dataType(Geoshape.class).make();
    }

    /**
     * Creates the composite indexes. A composite index is best used for
     * exact match lookups.
     */
    protected void createCompositeIndexes(final JanusGraphManagement management) {
        management.buildIndex("nameIndex", Vertex.class).addKey(management.getPropertyKey("name")).buildCompositeIndex();
    }

    /**
     * Adds the vertices, edges, and properties to the graph.
     */
    public void createElements() {
        try {
            // naive check if the graph was previously created
            if (g.V().has("name", "saturn").hasNext()) {
                if (supportsTransactions) {
                    g.tx().rollback();
                }
                return;
            }
            LOGGER.info("creating elements");

            // see GraphOfTheGodsFactory.java

            final Vertex saturn = g.addV("titan").property("name", "saturn").property("age", 10000).next();
            final Vertex sky = g.addV("location").property("name", "sky").next();
            final Vertex sea = g.addV("location").property("name", "sea").next();
            final Vertex jupiter = g.addV("god").property("name", "jupiter").property("age", 5000).next();
            final Vertex neptune = g.addV("god").property("name", "neptune").property("age", 4500).next();
            final Vertex hercules = g.addV("demigod").property("name", "hercules").property("age", 30).next();
            final Vertex alcmene = g.addV("human").property("name", "alcmene").property("age", 45).next();
            final Vertex pluto = g.addV("god").property("name", "pluto").property("age", 4000).next();
            final Vertex nemean = g.addV("monster").property("name", "nemean").next();
            final Vertex hydra = g.addV("monster").property("name", "hydra").next();
            final Vertex cerberus = g.addV("monster").property("name", "cerberus").next();
            final Vertex tartarus = g.addV("location").property("name", "tartarus").next();

            g.V(jupiter).as("a").V(saturn).addE("father").from("a").next();
            g.V(jupiter).as("a").V(sky).addE("lives").property("reason", "loves fresh breezes").from("a").next();
            g.V(jupiter).as("a").V(neptune).addE("brother").from("a").next();
            g.V(jupiter).as("a").V(pluto).addE("brother").from("a").next();

            g.V(neptune).as("a").V(sea).addE("lives").property("reason", "loves waves").from("a").next();
            g.V(neptune).as("a").V(jupiter).addE("brother").from("a").next();
            g.V(neptune).as("a").V(pluto).addE("brother").from("a").next();

            g.V(hercules).as("a").V(jupiter).addE("father").from("a").next();
            g.V(hercules).as("a").V(alcmene).addE("mother").from("a").next();

            if (supportsGeoshape) {
                g.V(hercules).as("a").V(nemean).addE("battled").property("time", 1)
                        .property("place", Geoshape.point(38.1f, 23.7f)).from("a").next();
                g.V(hercules).as("a").V(hydra).addE("battled").property("time", 2)
                        .property("place", Geoshape.point(37.7f, 23.9f)).from("a").next();
                g.V(hercules).as("a").V(cerberus).addE("battled").property("time", 12)
                        .property("place", Geoshape.point(39f, 22f)).from("a").next();
            } else {
                g.V(hercules).as("a").V(nemean).addE("battled").property("time", 1)
                        .property("place", getGeoFloatArray(38.1f, 23.7f)).from("a").next();
                g.V(hercules).as("a").V(hydra).addE("battled").property("time", 2)
                        .property("place", getGeoFloatArray(37.7f, 23.9f)).from("a").next();
                g.V(hercules).as("a").V(cerberus).addE("battled").property("time", 12)
                        .property("place", getGeoFloatArray(39f, 22f)).from("a").next();
            }

            g.V(pluto).as("a").V(jupiter).addE("brother").from("a").next();
            g.V(pluto).as("a").V(neptune).addE("brother").from("a").next();
            g.V(pluto).as("a").V(tartarus).addE("lives").property("reason", "no fear of death").from("a").next();
            g.V(pluto).as("a").V(cerberus).addE("pet").from("a").next();

            g.V(cerberus).as("a").V(tartarus).addE("lives").from("a").next();

            if (supportsTransactions) {
                g.tx().commit();
            }

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            if (supportsTransactions) {
                g.tx().rollback();
            }
        }
    }

    /**
     * Returns the geographical coordinates as a float array.
     */
    protected float[] getGeoFloatArray(final float lat, final float lon) {
        return new float[]{lat, lon};
    }

    /**
     * Runs some traversal queries to get data from the graph.
     */
    public void readElements() {
        try {
            if (g == null) {
                return;
            }

            LOGGER.info("reading elements");

            // look up vertex by name can use a composite index in JanusGraph
            final Optional<Map<Object, Object>> v = g.V().has("name", "jupiter").valueMap(true).tryNext();
            if (v.isPresent()) {
                LOGGER.info(v.get().toString());
            } else {
                LOGGER.warn("jupiter not found");
            }

            // look up an incident edge
            final Optional<Map<Object, Object>> edge = g.V().has("name", "hercules").outE("battled").as("e").inV()
                    .has("name", "hydra").select("e").valueMap(true).tryNext();
            if (edge.isPresent()) {
                LOGGER.info(edge.get().toString());
            } else {
                LOGGER.warn("hercules battled hydra not found");
            }

            // numerical range query can use a mixed index in JanusGraph
            final List<Object> list = g.V().has("age", P.gte(5000)).values("age").toList();
            LOGGER.info(list.toString());

            // pluto might be deleted
            final boolean plutoExists = g.V().has("name", "pluto").hasNext();
            if (plutoExists) {
                LOGGER.info("pluto exists");
            } else {
                LOGGER.warn("pluto not found");
            }

            // look up jupiter's brothers
            final List<Object> brothers = g.V().has("name", "jupiter").both("brother").values("name").dedup().toList();
            LOGGER.info("jupiter's brothers: " + brothers.toString());

        } finally {
            // the default behavior automatically starts a transaction for
            // any graph interaction, so it is best to finish the transaction
            // even for read-only graph query operations
            if (supportsTransactions) {
                g.tx().rollback();
            }
        }
    }

    /**
     * Makes an update to the existing graph structure. Does not create any
     * new vertices or edges.
     */
    public void updateElements() {
        try {
            if (g == null) {
                return;
            }
            LOGGER.info("updating elements");
            final long ts = System.currentTimeMillis();
            g.V().has("name", "jupiter").property("ts", ts).iterate();
            if (supportsTransactions) {
                g.tx().commit();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            if (supportsTransactions) {
                g.tx().rollback();
            }
        }
    }

    /**
     * Deletes elements from the graph structure. When a vertex is deleted,
     * its incident edges are also deleted.
     */
    public void deleteElements() {
        try {
            if (g == null) {
                return;
            }
            LOGGER.info("deleting elements");
            // note that this will succeed whether or not pluto exists
            g.V().has("name", "pluto").drop().iterate();
            if (supportsTransactions) {
                g.tx().commit();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            if (supportsTransactions) {
                g.tx().rollback();
            }
        }
    }

    /**
     * Closes the graph instance.
     */
    public void closeGraph() throws Exception {
        LOGGER.info("closing graph");
        try {
            if (g != null) {
                g.close();
            }
            if (graph != null) {
                graph.close();
            }
        } finally {
            g = null;
            graph = null;
        }
    }

    public void dropGraph() throws Exception {
        LOGGER.info("dropping graph");
        if (graph != null) {
            JanusGraphFactory.drop(graph);
        }
    }


    public void runApp() {
        try {
            openGraph();

            createSchema();

            // build the graph structure
            createElements();
            // read to see they were made
            Thread.sleep((long) (Math.random() * 500) + 500);
            readElements();

            for (int i = 0; i < 3; i++) {
                try {
                    Thread.sleep((long) (Math.random() * 500) + 500);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                }
                // update some graph elements with changes
                updateElements();
                // read to see the changes were made
                readElements();
            }

            // delete some graph elements
            deleteElements();
            // read to see the changes were made
            readElements();

            closeGraph();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

    }

    public static void main(String[] args) throws Exception {
        // open and initialize the graph
        final String fileName = (args != null && args.length > 0) ? args[0] : null;
        final boolean drop = (args != null && args.length > 1) && "drop".equalsIgnoreCase(args[1]);
        final TestJanusGraphFactory app = new TestJanusGraphFactory(fileName);
        if (drop) {
            app.openGraph();
            app.closeGraph();
        } else {
            app.runApp();
        }
    }
}
