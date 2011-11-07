package org.yalab.bourbon.tests

import junit.framework.Assert._
import android.test.AndroidTestCase

class UnitTests extends AndroidTestCase {
  def testPackageIsCorrect {
    assertEquals("org.yalab.bourbon", getContext.getPackageName)
  }
}
