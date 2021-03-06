/*
 *  Copyright WSO2 Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package barley.appmgt.impl.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.StackObjectPool;

import barley.appmgt.api.AppManagementException;

public class RemoteAuthorizationManager {

    private static final RemoteAuthorizationManager instance = new RemoteAuthorizationManager();

    private ObjectPool clientPool;

    private ScheduledExecutorService exec;
    private ScheduledFuture future;

    private RemoteAuthorizationManager() {

    }

    public static RemoteAuthorizationManager getInstance() {
        return instance;
    }

    public void init() {
        clientPool = new StackObjectPool(new BasePoolableObjectFactory() {
            @Override
            public Object makeObject() throws Exception {
                return new RemoteAuthorizationManagerClient();
            }
        });
    }

    public void destroy() {
        try {
            clientPool.close();
        } catch (Exception ignored) {
        }

    }

    public boolean isUserAuthorized(String user, String permission) throws AppManagementException {
        RemoteAuthorizationManagerClient client = null;
        try {
            user = AppManagerUtil.makeSecondaryUSNameDBFriendly(user);
            client = (RemoteAuthorizationManagerClient) clientPool.borrowObject();
            return client.isUserAuthorized(user, permission);

        } catch (Exception e) {
            throw new AppManagementException("Error while accessing backend services for WebApp key validation", e);
        } finally {
            try {
                if (client != null) {
                    clientPool.returnObject(client);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public String[] getRolesOfUser(String user) throws AppManagementException {
        RemoteAuthorizationManagerClient client = null;
        try {
            client = (RemoteAuthorizationManagerClient) clientPool.borrowObject();
            return client.getRolesOfUser(user);

        } catch (Exception e) {
            throw new AppManagementException("Error while retrieving role list of user", e);
        } finally {
            try {
                if (client != null) {
                    clientPool.returnObject(client);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public String[] getRoleNames() throws AppManagementException {
        RemoteAuthorizationManagerClient client = null;
        try {
            client = (RemoteAuthorizationManagerClient) clientPool.borrowObject();
            return client.getRoleNames();

        } catch (Exception e) {
            throw new AppManagementException("Error while retrieving the roles list of the system.", e);
        } finally {
            try {
                if (client != null) {
                    clientPool.returnObject(client);
                }
            } catch (Exception ignored) {
            }
        }
    }


}
