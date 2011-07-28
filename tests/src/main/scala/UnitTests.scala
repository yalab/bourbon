package org.yalab.bourbon.tests

import junit.framework.Assert._
import _root_.android.test.AndroidTestCase

class UnitTests extends AndroidTestCase {
  def testPackageIsCorrect {
    assertEquals("org.yalab.bourbon", getContext.getPackageName)
  }
}