package org.symera.source.iptv

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.symera.source.iptv.parser.IptvParseException
import org.symera.source.iptv.parser.IptvParseLimits
import org.symera.source.iptv.parser.XmlTvParser
import org.symera.source.iptv.parser.XmlTvParserOptions

class XmlTvParserTest {
    @Test
    fun parsesChannelsProgrammesAndTimeZones() {
        val xml = """
            <tv>
              <channel id="news"><display-name lang="en">News</display-name></channel>
              <programme channel="news" start="20260719120000 +0000" stop="20260719123000 +0000">
                <title lang="en">Noon News</title><category>News</category>
              </programme>
            </tv>
        """.trimIndent()

        val result = XmlTvParser().parse(StringReader(xml))

        assertEquals(1, result.value.channels.size)
        assertEquals(1, result.value.programmes.size)
        assertEquals("Noon News", result.value.programmes.single().titles.single().value)
        assertEquals(30 * 60 * 1_000L, result.value.programmes.single().range?.durationMillis)
    }

    @Test
    fun rejectsDoctypeAndExternalEntities() {
        val xml = """<!DOCTYPE tv [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><tv>&xxe;</tv>"""
        assertThrows(IptvParseException::class.java) { XmlTvParser().parse(StringReader(xml)) }
    }

    @Test
    fun enforcesTextLimit() {
        val xml = "<tv><channel id=\"one\"><display-name>Too long</display-name></channel></tv>"
        assertThrows(IptvParseException::class.java) {
            XmlTvParser(XmlTvParserOptions(limits = IptvParseLimits(maximumTextLength = 3)))
                .parse(StringReader(xml))
        }
    }
}
