require 'fileutils'

def install(file)
  puts "Installing: #{file}"
  target = File.join(File.dirname(__FILE__), '..', '..', '..', file)
  if File.exists?(target)
    puts "target #{target} already exists, skipping"
  else
    FileUtils.cp File.join(File.dirname(__FILE__), file), target
  end
end

install File.join( 'lib','java','migrator-rails.jar' )
