package com.microsoft.applicationinsights.telemetry;

import com.microsoft.applicationinsights.internal.schemav2.DataPoint;
import com.microsoft.applicationinsights.internal.schemav2.DataPointType;

import com.microsoft.applicationinsights.internal.schemav2.MetricData;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.internal.util.Sanitizer;

/**
 * Telemetry type used to track metrics.
 */
public final class MetricTelemetry extends BaseTelemetry<MetricData> {
    private final MetricData data;
    private final DataPoint metric;

    /**
     * Default constructor
     */
    public MetricTelemetry() {
        super();
        data = new MetricData();
        metric = new DataPoint();
        initialize(data.getProperties());
        data.getMetrics().add(metric);
    }

    /**
     * Initializes the instance with a name and value
     * @param name The name of the metric
     * @param value The value of the metric
     */
    public MetricTelemetry(String name, double value) {
        this();
        setName(name);
        metric.setValue(value);
    }

    /**
     * Gets the name of the metric.
     * @return The name of the metric.
     */
    public String getName() {
        return metric.getName();
    }

    /**
     * Sets the name of the metric.
     * @param name The name of the metric.
     */
    public void setName(String name) {
        if (Strings.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("The metric name cannot be null or empty");
        }

        metric.setName(name);
    }

    /**
     * Gets The value of the metric.
     * @return The value of the metric.
     */
    public double getValue() {
        return metric.getValue();
    }

    /**
     * Sets The value of the metric.
     * @param value The value of the metric.
     */
    public void setValue(double value) {
        metric.setValue(value);
    }

    /**
     * Gets the number of samples for this metric.
     * @return Number of samples.
     */
    public Integer getCount() {
        return metric.getCount();
    }

    /**
     * Sets the number of samples for this metric.
     * @param count Number of samples.
     */
    public void setCount(Integer count) {
        metric.setCount(count); updateKind();
    }

    /**
     * Gets the min value of this metric.
     * @return The min value.
     */
    public Double getMin() {
        return metric.getMin();
    }

    /**
     * Sets the min value of this metric.
     * @param value The min value.
     */
    public void setMin(Double value) {
        metric.setMin(value); updateKind();
    }

    /**
     * Gets the max value of this metric.
     * @return The max value.
     */
    public Double getMax() {
        return metric.getMax();
    }

    /**
     * Sets the max value of this metric.
     * @param value The max value.
     */
    public void setMax(Double value) {
        metric.setMax(value); updateKind();
    }

    /**
     * Gets the standard deviation of this metric.
     * @return The max value.
     */
    public Double getStandardDeviation() {
        return metric.getStdDev();
    }

    /**
     * Sets the standard deviation of this metric.
     * @param value The max value.
     */
    public void setStandardDeviation(Double value) {
        metric.setStdDev(value); updateKind();
    }

    @Override
    protected void additionalSanitize() {
        metric.setName(Sanitizer.sanitizeName(metric.getName()));
    }

    @Override
    protected MetricData getData() {
        return data;
    }

    private void updateKind() {
        boolean isAggregation =
            (metric.getCount() != null) ||
            (metric.getMin() != null) ||
            (metric.getMax() != null) ||
            (metric.getStdDev() != null);

        if ((metric.getCount() != null) && metric.getCount() == 1) {
            // Singular data point. This is not an aggregation.
            isAggregation = false;
        }

        metric.setKind(isAggregation ? DataPointType.Aggregation : DataPointType.Measurement);
    }
}
