package brooklyn.entity.database.postgresql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;

import com.google.common.collect.Iterables;

public class PostgreSqlNodeImpl extends SoftwareProcessImpl implements PostgreSqlNode {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlNodeImpl.class);

    private SshFeed feed;

    public Class<?> getDriverInterface() {
        return PostgreSqlDriver.class;
    }
    @Override
    public PostgreSqlDriver getDriver() {
        return (PostgreSqlDriver) super.getDriver();
    }
    
    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(EXECUTE_SCRIPT, new EffectorBody<String>() {
            @Override
            public String call(ConfigBag parameters) {
                return executeScript((String)parameters.getStringKey("commands"));
            }
        });
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(DATASTORE_URL, String.format("postgresql://%s:%s/", getAttribute(HOSTNAME), getAttribute(POSTGRESQL_PORT)));

        Location machine = Iterables.get(getLocations(), 0, null);

        if (machine instanceof SshMachineLocation) {
            String cmd = getDriver().getStatusCmd();

            feed = SshFeed.builder()
                    .entity(this)
                    .machine((SshMachineLocation)machine)
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command(cmd)
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .build();
        } else {
            LOG.warn("Location(s) %s not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
            setAttribute(SERVICE_UP, true);
        }
    }

    @Override
    protected void disconnectSensors() {
        if (feed != null) feed.stop();
    }
    
    @Override
    public String executeScript(String commands) {
        return getDriver().executeScriptAsync(commands).block().getStdout();
    }

}