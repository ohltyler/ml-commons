/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.util.Map;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.script.ScriptService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Function(FunctionName.REMOTE)
public class RemoteModel implements Predictable {

    public static final String CLUSTER_SERVICE = "cluster_service";
    public static final String SCRIPT_SERVICE = "script_service";
    public static final String CLIENT = "client";
    public static final String XCONTENT_REGISTRY = "xcontent_registry";

    private RemoteConnectorExecutor connectorExecutor;

    @VisibleForTesting
    RemoteConnectorExecutor getConnectorExecutor() {
        return this.connectorExecutor;
    }

    @Override
    public MLOutput predict(MLInput mlInput, MLModel model) {
        throw new IllegalArgumentException(
            "Model not ready yet. Please run this first: POST /_plugins/_ml/models/" + model.getModelId() + "/_deploy"
        );
    }

    @Override
    public MLOutput predict(MLInput mlInput) {
        if (!isModelReady()) {
            throw new IllegalArgumentException("Model not ready yet. Please run this first: POST /_plugins/_ml/models/<model_id>/_deploy");
        }
        try {
            return connectorExecutor.executePredict(mlInput);
        } catch (RuntimeException e) {
            log.error("Failed to call remote model", e);
            throw e;
        } catch (Throwable e) {
            log.error("Failed to call remote model", e);
            throw new MLException(e);
        }
    }

    @Override
    public void close() {
        this.connectorExecutor = null;
    }

    @Override
    public boolean isModelReady() {
        return connectorExecutor != null;
    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor) {
        try {
            Connector connector = model.getConnector().cloneConnector();
            connector.decrypt((credential) -> encryptor.decrypt(credential));
            this.connectorExecutor = MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class);
            this.connectorExecutor.setScriptService((ScriptService) params.get(SCRIPT_SERVICE));
            this.connectorExecutor.setClusterService((ClusterService) params.get(CLUSTER_SERVICE));
            this.connectorExecutor.setClient((Client) params.get(CLIENT));
            this.connectorExecutor.setXContentRegistry((NamedXContentRegistry) params.get(XCONTENT_REGISTRY));
        } catch (RuntimeException e) {
            log.error("Failed to init remote model", e);
            throw e;
        } catch (Throwable e) {
            log.error("Failed to init remote model", e);
            throw new MLException(e);
        }
    }

}
