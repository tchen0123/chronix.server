/*
 * GNU GENERAL PUBLIC LICENSE
 *                        Version 2, June 1991
 *
 *  Copyright (C) 1989, 1991 Free Software Foundation, Inc., <http://fsf.org/>
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *  Everyone is permitted to copy and distribute verbatim copies
 *  of this license document, but changing it is not allowed.
 */
package de.qaware.chronix.solr.query.analysis

import de.qaware.chronix.converter.BinaryTimeSeries
import de.qaware.chronix.converter.KassiopeiaSimpleConverter
import de.qaware.chronix.converter.TimeSeriesConverter
import de.qaware.chronix.timeseries.MetricTimeSeries
import de.qaware.chronix.timeseries.dt.DoubleList
import de.qaware.chronix.timeseries.dt.LongList
import org.apache.lucene.document.Document
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrDocumentList
import spock.lang.Specification

import java.nio.ByteBuffer

/**
 * @author f.lautenschlager
 */
class AnalysisDocumentBuilderTest extends Specification {

    def "test private constructor"() {
        when:
        AnalysisDocumentBuilder.newInstance()
        then:
        noExceptionThrown()
    }


    def "test collect"() {
        given:
        def docs = new SolrDocumentList()

        2.times {
            def document = new SolrDocument()
            document.addField("host", "laptop")
            document.addField("metric", "groovy")

            docs.add(document);
        }

        def fq = ["join=metric,host"] as String[]
        def joinFunction = JoinFunctionEvaluator.joinFunction(fq)

        when:
        def collectedDocs = AnalysisDocumentBuilder.collect(docs, joinFunction)

        then:
        collectedDocs.size() == 1
        collectedDocs.get("groovy-laptop").size() == 2
    }

    def "test analyze / aggregate"() {
        given:
        def docs = fillDocs()
        def analysis = AnalysisQueryEvaluator.buildAnalysis(["analysis=trend"] as String[])

        when:
        def ts = AnalysisDocumentBuilder.collectDocumentToTimeSeries(0l, Long.MAX_VALUE, docs);
        def document = AnalysisDocumentBuilder.analyze(analysis, "groovy-laptop", ts);

        then:
        document.getFieldValue("host") as String == "laptop"
        document.getFieldValue("metric") as String == "groovy"
        document.getFieldValue("start") as long == 1
        document.getFieldValue("end") as long == 1495
        document.getFieldValue("analysis") as String == "TREND"
        document.getFieldValue("analysisParam") as Object[] == new Object[0]
        document.getFieldValue("joinKey") as String == "groovy-laptop"
        document.getFieldValue("data") != null

        document.getFieldValue("someInt") as int == 1i
        document.getFieldValue("someFloat") as float == 1.1f
        document.getFieldValue("someDouble") as double == 2.0d
    }

    def "test analyze with subquery"() {
        given:
        def docs = fillDocs()
        def docs2 = fillDocs()
        def analysis = AnalysisQueryEvaluator.buildAnalysis(["analysis=fastdtw:(metric:*),10,0.5"] as String[])

        when:
        def ts = AnalysisDocumentBuilder.collectDocumentToTimeSeries(0l, Long.MAX_VALUE, docs);
        def ts2 = AnalysisDocumentBuilder.collectDocumentToTimeSeries(0l, Long.MAX_VALUE, docs2);
        def document = AnalysisDocumentBuilder.analyze(analysis, "groovy-laptop", ts, ts2);

        then:
        document.getFieldValue("host") as String == "laptop"
        document.getFieldValue("metric") as String == "groovy"
        document.getFieldValue("start") as long == 1
        document.getFieldValue("end") as long == 1495
        document.getFieldValue("analysis") as String == "FASTDTW"
        (document.getFieldValue("analysisParam") as Object[]).length == 3
        document.getFieldValue("joinKey") as String == "groovy-laptop"
        document.getFieldValue("data") != null

        document.getFieldValue("someInt") as int == 1i
        document.getFieldValue("someFloat") as float == 1.1f
        document.getFieldValue("someDouble") as double == 2.0d
    }

    List<Document> fillDocs() {
        def result = new ArrayList<SolrDocument>();

        TimeSeriesConverter<MetricTimeSeries> converter = new KassiopeiaSimpleConverter();

        10.times {
            MetricTimeSeries ts = new MetricTimeSeries.Builder("groovy")
                    .attribute("host", "laptop")
                    .points(times(it + 1), values(it + 1))
                    .build();
            def doc = converter.to(ts)
            result.add(asSolrDoc(doc))
        }

        result
    }

    def SolrDocument asSolrDoc(BinaryTimeSeries binaryStorageDocument) {
        def doc = new SolrDocument()
        doc.addField("host", binaryStorageDocument.get("host"))
        doc.addField("data", ByteBuffer.wrap(binaryStorageDocument.getPoints()))
        doc.addField("metric", binaryStorageDocument.get("metric"))
        doc.addField("start", binaryStorageDocument.getStart())
        doc.addField("end", binaryStorageDocument.getEnd())
        doc.addField("someInt", 1i)
        doc.addField("someFloat", 1.1f)
        doc.addField("someDouble", 2.0d)

        doc
    }

    def LongList times(int i) {
        def times = new LongList()
        100.times {
            times.add(it * 15 + i as long)
        }
        times
    }

    def DoubleList values(int i) {
        def values = new DoubleList()

        100.times {
            values.add(it * 100 * i as double)
        }
        values
    }
}