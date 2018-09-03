Python SSA Profiler
==========


Usage
----
Download Antlr jar file at http://www.antlr.org/download.html

For IDE:

1.Download repo and open it with IDE

2.Include downloaded antlr jar file as external dependency;

run with "gui" to show parse tree;

run with "driver" to generate output file;

-For command line:
1.Set path to "AntlrPython" direntory and run:

    Windows    
    $ javac -cp "<your Antlr jar Path>" src\*.java
    $ java -cp .;"<your Antlr jar Path>";src Driver input.py
    Linux 
    $ javac -cp "<your Antlr jar Path>" src\*.java
    $ java -cp .:"<your Antlr jar Path>":src Driver input.py
     
   
    
The instrumented file "out.py" will appear at the "AntlrPython" direntory

You also need to create "phi.py" for your Python code to be profile since "out.py" import phi

More info for setting up Antlr: https://tomassetti.me/antlr-mega-tutorial/