package org.jruby.webapp;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContext;

import org.jruby.Ruby;

/**
 * MigratorContextListener - migrates the production schema upon web deploy
 *
 * @author Mike Hagedorn, http://www.silverchairsolutions.com
 * @version 0.9
 */
public class MigratorContextListener implements ServletContextListener {

    
    

		public void contextInitialized(ServletContextEvent event) {
			ServletContext context = event.getServletContext();
			
				System.out.println("Entering: MigratorContentListener.contextIntialized \n");

					Ruby runtime = Ruby.getDefaultInstance();

					/* This script runs the migration when the context loads */

					String script = 
						"require 'java'\n" +
						"require 'rubygems'\n"+
						"gem 'activerecord'\n"+
						"ActiveRecord::Migrator.migrate('db/migrate/',nil)\n";
							
					runtime.evalScriptlet(script);
	   
		}

		public void contextDestroyed(ServletContextEvent event) {
		}

	}

