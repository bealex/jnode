package org.jnode.rest.auth;

import jnode.logger.Logger;
import jnode.orm.ORMManager;
import org.jnode.rest.db.RestUser;
import org.jnode.rest.di.Named;
import org.jnode.rest.di.Singleton;

import java.util.Date;


@Named("prod-pwdProvider")
@Singleton
public class PwdProviderImpl implements PwdProvider{

    private static final Logger LOGGER = Logger.getLogger(PwdProviderImpl.class);

    @Override
    public RestUser isAuth(String token) {
        RestUser user = ORMManager.get(RestUser.class)
                .getFirstAnd(RestUser.TOKEN_FIELD, "=", token);

        LOGGER.l5(String.format("for token %s get %s", token, user));

        if (user == null) {
            return null;
        }

        user.setLastLogin(new Date());
        ORMManager.get(RestUser.class).update(user);

        return user;
    }
}