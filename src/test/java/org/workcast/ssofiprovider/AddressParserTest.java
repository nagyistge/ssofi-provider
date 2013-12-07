package org.workcast.ssofiprovider;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(OpenIDHandler.class)
public class AddressParserTest {

	@Before
	public void before() throws Exception {
		mockStatic(OpenIDHandler.class);
		when(OpenIDHandler.getBaseURL()).thenReturn("base-url/");
		AddressParser.initialize(null);
	}

	@Test
	public void isRoot_addressWithRootOnly_true() throws Exception {

		String address = "base-url/";
		AddressParser parser = new AddressParser(address);

		final boolean isRoot = parser.isRoot();

		assertThat(isRoot, is(true));
	}

	@Test
	public void getUserId_addressWithId_returnsUserId() throws Exception {

		String address = "base-url/id";
		AddressParser parser = new AddressParser(address);

		final String userId = parser.getUserId();

		assertThat(userId, equalTo("id"));
	}
}
