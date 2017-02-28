package methods;

import rest.RestCommand;
import rest.RestResult;
import rest.UnsecureRestCommand;

import static methods.Seq.next;

public final class User {
    private User() {
    }

    public static RestResult login(String login, String password) {

        RestCommand userLoginCmd = new UnsecureRestCommand(String.format("{\n" +
                "    \"method\": \"user.login\",\n" +
                "    \"params\": {\n" +
                "        \"login\": \"%s\",\n" +
                "        \"password\": \"%s\"\n" +
                "    },\n" +
                "    \"id\": %d,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}", login, password, next()));
        return userLoginCmd.execute();

    }

}