package org.yalab.bourbon.tests

import org.yalab.bourbon._
import junit.framework.Assert._
import android.test.AndroidTestCase
import android.test.ProviderTestCase2
import android.content.Context
import scala.xml.XML

class UnitTests extends AndroidTestCase {
  def testPackageIsCorrect {
    assertEquals("org.yalab.bourbon", getContext.getPackageName)
  }

  def testVOARssParse {
    val testContext = classOf[UnitTests].getMethod("getTestContext").invoke(this).asInstanceOf[Context]
    val xml = XML.load(testContext.getAssets.open("articles.rss"))
    val voaRss = ArticleProvider.VOARss(xml)
    println(voaRss.parse)
    assertEquals(1, 1)
  }
}

