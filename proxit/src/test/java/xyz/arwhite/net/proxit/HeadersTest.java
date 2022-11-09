package xyz.arwhite.net.proxit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HeadersTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {

	}

	@Test
	void emptyListCheck() {
		var x = Headers.parse(new ArrayList<String>());
		assertNotNull(x);
		assertEquals(0,x.size());
	}

	@Test
	void duplicateHeaderCheck() {
		var headerStrings = Arrays.asList("fred:bloggs", "fred:wilma");

		var x = Headers.parse(headerStrings);
		commonChecks(x);
	}

	@Test
	void whiteSpaceHeaderCheck() {
		var headerStrings = Arrays.asList(" fred:bloggs", "fred: wilma");

		var x = Headers.parse(headerStrings);
		commonChecks(x);
	}

	void commonChecks(Map<String,List<String>> x) {
		assertNotNull(x);
		assertEquals(1,x.size());

		assertNotNull(x.get("fred"));
		assertEquals(2,x.get("fred").size());
		assertNotNull(x.get("fred").get(0));
		assertEquals("bloggs",x.get("fred").get(0));

		assertNotNull(x.get("fred").get(1));
		assertEquals("wilma",x.get("fred").get(1));
	}

}
