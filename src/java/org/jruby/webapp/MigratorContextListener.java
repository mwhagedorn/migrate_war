package org.jruby.webapp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jruby.RubyRuntimeAdapter;
import org.jruby.javasupport.JavaEmbedUtils;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;




import org.jruby.Ruby;

/**
 * MigratorContextListener - migrates the production schema upon web deploy
 *
 * @author Mike Hagedorn, http://www.silverchairsolutions.com
 * @version 0.9
 */
public class MigratorContextListener implements ServletContextListener {

    String commandFile;
    String commandClass;
    String commandMethod;
    private RubyRuntimeAdapter runtimeApi;
    private Thread thread = null;
        private static final int FIFTEEN_MINUTES_IN_MILLIS = 1000 * 60 * 15;
    	private ObjectPool runtimePool;
          private boolean stop = false; // wait 30 seconds

    public void contextInitialized(ServletContextEvent event) {
        try {
            final ServletContext context = event.getServletContext();
            // create the pool
            initiallizeJrubyEnvironment(context);

            commandFile = context.getInitParameter("command-file");


            System.out.println("Entering: MigratorContentListener.contextIntialized \n");

            thread = new Thread(new Runnable() {

                public void run() {
                    try {
                        // wait for a little while before starting the task
                        // this allow the app server to start serving requests before initializing all tasks
                        Thread.sleep(100);


                        runOnce(context);
                        System.out.println("Exiting: MigratorContentListener.contextIntialized \n");
                    } catch (InterruptedException e) {
                        // break out of loop
                    } catch (Exception e) {
                        context.log("Could not start " + commandFile, e);
                    }
                }
            });
            thread.start();
        } catch (ServletException ex) {
            Logger.getLogger(MigratorContextListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private ObjectPool getRuntimePool() {
       return this.runtimePool;
    }

    private void initiallizeJrubyEnvironment(final ServletContext context) throws ServletException {

        // find the root of the web application
        String railsRoot = getPath("/", context);

        // create the factory
        RailsFactory railsFactory = new RailsFactory();
        railsFactory.setRailsRoot(railsRoot);
        railsFactory.setRailsEnvironment(getDeploymentEnvironment());
        railsFactory.setServletContext(context);

        if (isStandalone()) {
            railsFactory.removeEnvironment("GEM_HOME");
        }
        railsFactory.setGemPath(findGemPath(context));



        // create the pool
        createObjectPool(railsFactory);
         runtimeApi = JavaEmbedUtils.newRuntimeAdapter();
    }

    private void runOnce(ServletContext context) throws Exception {



        try {
            String rootDir = context.getRealPath("");

            Ruby runtime = null;
            try {

                String script = readFileAsString(rootDir + "/" + commandFile);
                context.log("executing "+script);
                runtime = (Ruby) getRuntimePool().borrowObject();
                runtimeApi.eval(runtime,"ENV['RAILS_ROOT'] = '" + rootDir + "'");
                runtimeApi.eval(runtime, script);
                getRuntimePool().returnObject(runtime);
            } catch (Exception e) {
               context.log("Could not execute: " + commandFile, e);
                getRuntimePool().invalidateObject(runtime);
              context.log(commandFile + " returning JRuby runtime to pool and will restart in 15 minutes.");
                try {
                    Thread.sleep(FIFTEEN_MINUTES_IN_MILLIS);
                } catch (InterruptedException ex) {
                // can't do much here ...
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            context.log("Could not execute: " + commandFile, e);
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        stop = true;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
        }

    }

    private static String readFileAsString(String filePath)
            throws java.io.IOException {
        StringBuffer fileData = new StringBuffer(1000);
        BufferedReader reader = new BufferedReader(
                new FileReader(filePath));
        char[] buf = new char[1024];
        int numRead = 0;
        while ((numRead = reader.read(buf)) != -1) {
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
        }
        reader.close();
        return fileData.toString();
    }

    private String findGemPath(ServletContext context) {
        if (isStandalone()) {
            // look for a local copy to override the default
            try {
                return getPath("/WEB-INF/gems",context);
            } catch (ServletException e) {
            // webapp doesn't include any gems
            }
        } else {
            // try other locations is this is not standalone
            String gemPath = System.getProperty("gem.path");
            if (gemPath != null && gemPath.length() > 0) {
                return gemPath;
            }
            gemPath = System.getProperty("gem.home");
            if (gemPath != null && gemPath.length() > 0) {
                return gemPath;
            }
        }
        return null;
    }

    public boolean isStandalone() {
        return true;
    }

    protected String getDeploymentEnvironment() {
        return "production";
    }

    /**
     * Locate a relative webapp path on the file system.
     */
    private String getPath(String path, ServletContext context) throws ServletException {
        String realPath = context.getRealPath(path);
        if (realPath == null) {
            throw new ServletException("Could not find resource " + path);
        }
        // remove any trailing slash
        if (realPath.endsWith("/")) {
            realPath = realPath.substring(0, realPath.length() - 1);
        }
        return realPath;
    }

    /**
     * Create the pool of JRuby runtimes.
     */
    protected void createObjectPool(RailsFactory railsFactory) {
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        // when the server is loaded, pausing for the request makes it worse
        // instead, we'll just fail and return a message to the user
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_FAIL;
        config.maxActive = 4;
        config.maxIdle = 4;
        config.minIdle = 2;
        config.timeBetweenEvictionRunsMillis = 10000;
        // create the pool
        runtimePool = new GenericObjectPool(railsFactory, config);
        // preload a minimum number of objects
        Runnable preloader = new Preloader(config.minIdle);
        new Thread(preloader).start();
    }
    
    /**
	 * Preload to ensure we have at least a minimum number of objects available,
	 * the evictor will create more as required if and when it starts
	 */
	private class Preloader implements Runnable {

		private int minObjects;

		public Preloader(int minObjects) {
			this.minObjects = minObjects;
		}

		public void run() {
			try {
				while (runtimePool.getNumIdle() + runtimePool.getNumActive() < minObjects) {
					runtimePool.addObject();
					// small delay between starting
					try {
						Thread.sleep(100);
					} catch (InterruptedException ignore) {
					}
				}
			} catch (Exception e) {
                                e.printStackTrace();
				System.out.println("Failed to preload JRuby: " + e.getMessage());
			}
		}
	}

}

