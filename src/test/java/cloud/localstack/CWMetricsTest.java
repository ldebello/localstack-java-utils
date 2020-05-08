package cloud.localstack;

import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.cloudwatch.model.StatisticSet;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Test integration of CloudWatch metrics with LocalStack
 * Issue: https://github.com/localstack/localstack/issues/712
 */
@RunWith(LocalstackTestRunner.class)
@LocalstackDockerProperties(ignoreDockerRunErrors = true)
public class CWMetricsTest {

    private static final String METRIC_NAME = "Example";

    private static final String NAMESPACE = "Acme/Monitoring";

    @Test
    public void testCWMetricsAPIs() throws ParseException {
        final AmazonCloudWatch cw = TestUtils.getClientCloudWatch();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        Dimension dimension = new Dimension()
                .withName("UNIQUE_PAGES")
                .withValue("URLS");

        /* Put metric data without value */
        MetricDatum datum = new MetricDatum()
                .withMetricName("PAGES_VISITED")
                .withUnit(StandardUnit.None)
                .withTimestamp(dateFormat.parse("2019-01-02"))
                .withDimensions(dimension);

        PutMetricDataRequest putMetricDataRequest = new PutMetricDataRequest()
                .withNamespace("SITE/TRAFFIC")
                .withMetricData(datum);

        cw.putMetricData(putMetricDataRequest);

        /* Get metric statistics */
        GetMetricStatisticsRequest getMetricStatisticsRequest = new GetMetricStatisticsRequest()
                .withMetricName("PAGES_VISITED")
                .withNamespace("SITE/TRAFFIC")
                /* When calling GetMetricStatistics, must specify either Statistics or ExtendedStatistics, but not both.
                   https://docs.aws.amazon.com/cli/latest/reference/cloudwatch/get-metric-statistics.html */
                .withStatistics("Statistics")
                .withStartTime(dateFormat.parse("2019-01-01"))
                .withEndTime(dateFormat.parse("2019-01-03"))
                .withPeriod(360);

        GetMetricStatisticsResult metricStatistics = cw.getMetricStatistics(getMetricStatisticsRequest);
        assertEquals(metricStatistics.getDatapoints().size(), 1);

        /* List metric work as expectation */
        ListMetricsResult metrics = cw.listMetrics(new ListMetricsRequest());
        assertEquals(metrics.getMetrics().size(), 1);
    }

    @Test
    public void testCWMetricsWithStatisticsAPIs() {
        final AmazonCloudWatch cw = TestUtils.getClientCloudWatch();
        Date start = new Date();

        Set<Dimension> dimensions = new HashSet<>();

        dimensions.add(new Dimension().withName("Dim1").withValue("Value1"));
        dimensions.add(new Dimension().withName("Dim2").withValue("Value2"));
        dimensions.add(new Dimension().withName("Dim3").withValue("Value3"));

        StatisticSet set = new StatisticSet()
                .withMaximum(100.0)
                .withMinimum(75.0)
                .withSampleCount(2.0)
                .withSum(85.0);

        MetricDatum metricDatum = new MetricDatum()
                .withMetricName(METRIC_NAME)
                .withTimestamp(start)
                .withDimensions(dimensions)
                .withStatisticValues(set);

        PutMetricDataRequest putMetricDataRequest = new PutMetricDataRequest()
                .withNamespace(NAMESPACE)
                .withMetricData(metricDatum);


        cw.putMetricData(putMetricDataRequest);

        GetMetricStatisticsRequest metricStatisticsRequest = new GetMetricStatisticsRequest()
                .withNamespace(NAMESPACE)
                .withDimensions(dimensions)
                .withStartTime(start)
                .withEndTime(new Date())
                .withMetricName(METRIC_NAME)
                .withPeriod(60)
                .withStatistics(Statistic.Minimum, Statistic.Maximum, Statistic.SampleCount, Statistic.Sum, Statistic.Average);

        GetMetricStatisticsResult metricStatistics = cw.getMetricStatistics(metricStatisticsRequest);

        assertThat(metricStatistics.getDatapoints().size(), is(1));
        assertThat(metricStatistics.getDatapoints().get(0).getMaximum(), is(not(0.0)));
    }
}
