package io.digdag.cli;

import java.util.List;
import java.util.Map;
import java.io.File;
import java.time.ZoneId;
import javax.servlet.ServletException;
import javax.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import io.digdag.guice.rs.GuiceRsBootstrap;
import io.digdag.guice.rs.GuiceRsServerControl;
import io.digdag.server.ServerModule;
import io.digdag.core.DigdagEmbed;
import io.digdag.core.LocalSite;
import io.digdag.client.config.ConfigFactory;

public class ServerBootstrap
    implements GuiceRsBootstrap
{
    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);

    private GuiceRsServerControl control;

    @Inject
    public ServerBootstrap(GuiceRsServerControl control)
    {
        this.control = control;
    }

    @Override
    public Injector initialize(ServletContext context)
    {
        Injector injector = new DigdagEmbed.Bootstrap()
            .addModules(new ServerModule())
            .addModules((binder) -> {
                binder.bind(ArgumentConfigLoader.class).in(Scopes.SINGLETON);
                binder.bind(RevisionAutoReloader.class).in(Scopes.SINGLETON);
            })
            .initialize()
            .getInjector();

        // TODO create global site
        LocalSite site = injector.getInstance(LocalSite.class);

        String autoLoadFile = context.getInitParameter("io.digdag.cli.server.autoLoadFile");
        if (autoLoadFile != null) {
            ConfigFactory cf = injector.getInstance(ConfigFactory.class);
            RevisionAutoReloader autoReloader = injector.getInstance(RevisionAutoReloader.class);
            try {
                autoReloader.loadFile(new File(autoLoadFile), ZoneId.systemDefault(), cf.create());
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        // start server
        site.startLocalAgent();
        site.startMonitor();

        Thread thread = new Thread(() -> {
            try {
                site.run();
            }
            catch (Exception ex) {
                logger.error("Uncaught error", ex);
                control.destroy();
            }
        }, "local-site");
        thread.setDaemon(true);
        thread.start();

        return injector;
    }
}