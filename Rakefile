# taken from https://projects.jkraemer.net/svn/plugins/jruby/log4j_logger

require 'rake'
require 'java'

task :default => :build_jar

# compile task from http://blog.foemmel.com/jrake/compiling

CLASSES_DIR = 'tmp/classes'

task :build_jar => [ :compile ] do
  jar CLASSES_DIR, 'lib/migrator-rails-0.9.jar'
end

task :compile do
  src_dir = 'src/java'
  dest_dir = CLASSES_DIR
  
  
 #Dir::mkdir(dest_dir) unless File::exist?(dest_dir)
`mkdir -pv tmp/classes` unless File::exist?(dest_dir)


  javac(src_dir, dest_dir)
end

def jar(src_dir, target)
  `jar cf #{target} -C #{src_dir} .`
end

def build_class_path
  FileList["dependencies/*.jar"].map{|f| File.expand_path f}.join(':')
  
end

def javac(src_dir, dest_dir)
  #java_files = get_out_of_date_files(src_dir, dest_dir)
  java_files = FileList["#{src_dir}/**/*.java"]

  unless java_files.empty?
    print "compiling #{java_files.size} java file(s)..."
    #print build_class_path
    args = [ '-cp', build_class_path, '-d', dest_dir, *java_files ]
print "#{args}"
    buf = java.io.StringWriter.new
    if com.sun.tools.javac.Main.compile(to_java_array(java.lang.String, args), 
                                        java.io.PrintWriter.new(buf)) != 0
      print "FAILED\n\n"
      print buf.to_s
      print "\n"
      fail 'Compile failed'
    end
    print "done\n"
  end
end

def get_out_of_date_files(src_dir, dest_dir)
  java_files = []
  FileList["#{src_dir}/**/*.java"].each do |java_file|
    class_file = dest_dir + java_file[src_dir.length, java_file.length - src_dir.length - '.java'.length] + '.class'
    
    # todo: figure out why File.ctime doesn't work
    unless File.exist?(class_file) && java.io.File.new(class_file).lastModified > java.io.File.new(java_file).lastModified
      java_files << java_file
    end
  end
  return java_files
end

def to_java_array(element_type, ruby_array)
  java_array = java.lang.reflect.Array.newInstance(element_type, ruby_array.size)
  ruby_array.each_index { |i| java_array[i] = ruby_array[i] }
  return java_array
end
