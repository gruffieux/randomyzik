package com.gbrfix.randomyzik;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Created by gab on 04.10.2017.
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({PlaylistDbTest.class, MediaDAOTest.class, AmpSessionTest.class, PlaylistTest.class, AmpPlaylistTest.class})
public class UnitTestSuite {
}
