/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.WildcardQueryBuilder;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

import lombok.Getter;
import lombok.Setter;

@ToolAnnotation(SearchAnomalyDetectorsTool.TYPE)
public class SearchAnomalyDetectorsTool implements Tool {
    public static final String TYPE = "SearchAnomalyDetectorsTool";
    private static final String DEFAULT_DESCRIPTION = "Use this tool to search anomaly detectors.";

    @Setter
    @Getter
    private String name = TYPE;
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    private String type;
    @Getter
    private String version;

    private Client client;
    @Setter
    private Parser<?, ?> inputParser;
    @Setter
    private Parser<?, ?> outputParser;

    public SearchAnomalyDetectorsTool(Client client) {
        this.client = client;

        // probably keep this overridden output parser. need to ensure the output matches what's expected
        outputParser = new Parser<>() {
            @Override
            public Object parse(Object o) {
                @SuppressWarnings("unchecked")
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        };
    }

    // TODO: finish logic in run()
    // Response is currently in a simple string format including the list of anomaly detectors (only name and ID attached), and
    // number of total detectors. The output will likely need to be updated, standardized, and include more fields in the
    // future to cover a sufficient amount of potential questions the agent will need to handle.
    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        final String detectorName = parameters.getOrDefault("monitorName", null);
        final String detectorNamePattern = parameters.getOrDefault("detectorNamePattern", null);
        final String indices = parameters.getOrDefault("indices", null);
        final Boolean highCardinality = parameters.containsKey("highCardinality") ? Boolean.parseBoolean(parameters.get("highCardinality")) : null;
        final Long lastUpdateTime = parameters.getOrDefault("lastUpdateTime", null);
        final String sortOrderStr = parameters.getOrDefault("sortOrder", "asc");
        final SortOrder sortOrder = sortOrderStr == "asc" ? SortOrder.ASC : SortOrder.DESC;
        final String sortString = parameters.getOrDefault("sortString", "monitor.name.keyword");
        final int size = parameters.containsKey("size") ? Integer.parseInt(parameters.get("size")) : 20;
        final int startIndex = parameters.containsKey("startIndex") ? Integer.parseInt(parameters.get("startIndex")) : 0;
        final Boolean running = parameters.containsKey("running") ? Boolean.parseBoolean(parameters.get("running")) : null;
        final Boolean disabled = parameters.containsKey("disabled") ? Boolean.parseBoolean(parameters.get("disabled")) : null;
        final Boolean failed = parameters.containsKey("failed") ? Boolean.parseBoolean(parameters.get("failed")) : null;

        // List<QueryBuilder> mustList = new ArrayList<QueryBuilder>();
        // if (monitorName != null) {
        //     mustList.add(new TermQueryBuilder("monitor.name.keyword", monitorName));
        // }
        // if (monitorNamePattern != null) {
        //     mustList.add(new WildcardQueryBuilder("monitor.name.keyword", monitorNamePattern));
        // }
        // if (enabled != null) {
        //     mustList.add(new TermQueryBuilder("monitor.enabled", enabled));
        // }
        // if (hasTriggers != null) {
        //     NestedQueryBuilder nestedTriggerQuery = new NestedQueryBuilder(
        //         "monitor.triggers",
        //         new ExistsQueryBuilder("monitor.triggers"),
        //         null
        //     );
        //     BoolQueryBuilder triggerQuery = new BoolQueryBuilder();
        //     if (hasTriggers) {
        //         triggerQuery.must(nestedTriggerQuery);
        //     } else {
        //         triggerQuery.mustNot(nestedTriggerQuery);
        //     }
        //     mustList.add(triggerQuery);
        // }
        // if (indices != null) {
        //     mustList
        //         .add(
        //             new NestedQueryBuilder("monitor.inputs", new WildcardQueryBuilder("monitor.inputs.search.indices", indices), null)
        //         );
        // }

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must().addAll(mustList);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .query(boolQueryBuilder)
            .size(size)
            .from(startIndex)
            .sort(sortString, sortOrder);

        SearchMonitorRequest searchMonitorRequest = new SearchMonitorRequest(new SearchRequest().source(searchSourceBuilder));

        ActionListener<SearchResponse> searchMonitorListener = ActionListener.<SearchResponse>wrap(response -> {
            StringBuilder sb = new StringBuilder();
            SearchHit[] hits = response.getHits().getHits();
            sb.append("Monitors=[");
            for (SearchHit hit : hits) {
                sb.append("{");
                sb.append("id=").append(hit.getId()).append(",");
                sb.append("name=").append(hit.getSourceAsMap().get("name"));
                sb.append("}");
            }
            sb.append("]");
            sb.append("TotalMonitors=").append(response.getHits().getTotalHits().value);
            listener.onResponse((T) sb.toString());
        }, e -> { listener.onFailure(e); });
        AlertingPluginInterface.INSTANCE.SearchAnomalyDetectors((NodeClient) client, searchMonitorRequest, searchMonitorListener);
        
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Factory for the {@link SearchAnomalyDetectorsTool}
     */
    public static class Factory implements Tool.Factory<SearchAnomalyDetectorsTool> {
        private Client client;

        private static Factory INSTANCE;

        /** 
         * Create or return the singleton factory instance
         */
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (SearchAnomalyDetectorsTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        /**
         * Initialize this factory
         * @param client The OpenSearch client
         */
        public void init(Client client) {
            this.client = client;
        }

        @Override
        public SearchAnomalyDetectorsTool create(Map<String, Object> map) {
            return new SearchAnomalyDetectorsTool(client);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

}