require 'fileutils'

def install(file)
  puts "Installing: #{file}"
  base = File.basename(file)
  target = File.join(File.dirname(__FILE__), '..', '..', '..','lib','java',base)

  if File.exists?(target)
    puts "target #{target} already exists, skipping"
  else
    FileUtils.cp File.join(File.dirname(__FILE__), file), target
  end
end

dest_dir = 'lib/java'
   
Dir::mkdir(dest_dir) unless File::exist?(dest_dir)

install File.join( 'lib','migrator-rails.jar' )
