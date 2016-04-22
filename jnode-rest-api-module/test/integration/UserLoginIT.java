package integration;

import org.jnode.rest.core.Http;
import org.jnode.rest.core.RPCError;
import org.junit.Test;
import rest.RestCommand;
import rest.RestResult;
import rest.UnsecureRestCommand;

import static methods.Seq.next;
import static methods.User.login;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class UserLoginIT {
    @Test
    public void userLogin() throws Exception {
        RestResult restResult = login("2:5020/828.17", "111111");

        assertThat(restResult, is(notNullValue()));
        assertThat(restResult.getHttpCode(), is(Http.OK));
        assertThat(restResult.getPayload(), is(notNullValue()));
        assertThat(restResult.getPayload().getResult(), is(instanceOf(String.class)));
    }

    @Test
    public void userEmptyLogin() throws Exception {
        RestResult restResult = login("", "111111");

        System.out.println(restResult);
        assertThat(restResult, is(notNullValue()));
        assertThat(restResult.getHttpCode(), is(Http.OK));
        assertThat(restResult.getPayload(), is(notNullValue()));
        assertThat(restResult.getPayload().getError(), is(notNullValue()));
        assertThat(restResult.getPayload().getError().getCode(), is(RPCError.CODE_EMPTY_LOGIN));
    }

    @Test
    public void userNullLogin() throws Exception {

        RestCommand userLoginCmd = new UnsecureRestCommand(String.format("{\n" +
                "    \"method\": \"user.login\",\n" +
                "    \"params\": {\n" +
                "        \"login\": null,\n" +
                "        \"password\": \"%s\"\n" +
                "    },\n" +
                "    \"id\": %d,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}",  "dummy", next()));

        RestResult restResult = userLoginCmd.execute();


        System.out.println(restResult);
        assertThat(restResult, is(notNullValue()));
        assertThat(restResult.getHttpCode(), is(Http.OK));
        assertThat(restResult.getPayload(), is(notNullValue()));
        assertThat(restResult.getPayload().getError(), is(notNullValue()));
        assertThat(restResult.getPayload().getError().getCode(), is(RPCError.CODE_INVALID_PARAMS));
    }

}
