/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.clustering;

import static org.opensearch.ml.engine.helper.MLTestHelper.constructTestDataFrame;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.output.MLPredictionOutput;

public class KMeansTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private KMeansParams parameters;
    private KMeans kMeans;
    private DataFrame trainDataFrame;
    private DataFrameInputDataset trainDataFrameInputDataSet;
    private MLInput trainDataFrameInput;
    private DataFrame predictionDataFrame;
    private DataFrameInputDataset predictionDataFrameInputDataset;
    private MLInput predictionDataFrameInput;
    private int trainSize = 100;
    private int predictionSize = 10;

    @Before
    public void setUp() {
        parameters = KMeansParams.builder().distanceType(KMeansParams.DistanceType.EUCLIDEAN).iterations(10).centroids(2).build();

        kMeans = new KMeans(parameters);
        constructKMeansTrainDataFrame();
        constructKMeansPredictionDataFrame();
    }

    @Test
    public void predict() {
        MLModel model = kMeans.train(trainDataFrameInput);
        MLPredictionOutput output = (MLPredictionOutput) kMeans.predict(predictionDataFrameInput, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(predictionSize, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void predictWithNullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for KMeans prediction");
        kMeans.predict(predictionDataFrameInput, null);
    }

    @Test
    public void train() {
        MLModel model = kMeans.train(trainDataFrameInput);
        Assert.assertEquals(FunctionName.KMEANS.name(), model.getName());
        Assert.assertEquals("1.0.0", model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainAndPredict() {
        KMeansParams parameters = KMeansParams
            .builder()
            .distanceType(KMeansParams.DistanceType.EUCLIDEAN)
            .iterations(10)
            .centroids(2)
            .build();
        KMeans kMeans = new KMeans(parameters);
        MLPredictionOutput output = (MLPredictionOutput) kMeans.trainAndPredict(trainDataFrameInput);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(trainSize, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));

        parameters = parameters.toBuilder().distanceType(KMeansParams.DistanceType.COSINE).build();
        kMeans = new KMeans(parameters);
        output = (MLPredictionOutput) kMeans.trainAndPredict(trainDataFrameInput);
        predictions = output.getPredictionResult();
        Assert.assertEquals(trainSize, predictions.size());

        parameters = parameters.toBuilder().distanceType(KMeansParams.DistanceType.L1).build();
        kMeans = new KMeans(parameters);
        output = (MLPredictionOutput) kMeans.trainAndPredict(trainDataFrameInput);
        predictions = output.getPredictionResult();
        Assert.assertEquals(trainSize, predictions.size());
    }

    @Test
    public void constructorWithNegtiveCentroids() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("K should be positive");
        new KMeans(KMeansParams.builder().centroids(-1).build());
    }

    @Test
    public void constructorWithNegtiveIterations() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Iterations should be positive");
        new KMeans(KMeansParams.builder().iterations(-1).build());
    }

    private void constructKMeansPredictionDataFrame() {
        predictionDataFrame = constructTestDataFrame(predictionSize);
        predictionDataFrameInputDataset = new DataFrameInputDataset(predictionDataFrame);
        predictionDataFrameInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(predictionDataFrameInputDataset).build();
    }

    private void constructKMeansTrainDataFrame() {
        trainDataFrame = constructTestDataFrame(trainSize);
        trainDataFrameInputDataSet = new DataFrameInputDataset(trainDataFrame);
        trainDataFrameInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(trainDataFrameInputDataSet).build();
    }

}
