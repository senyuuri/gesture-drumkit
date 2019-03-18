# gesture-drumkit
a real-time gesture based interactive drum kit

The watch app & android app communicate through protobuf.  
The watch app in particular uses [Nanopb](https://github.com/nanopb/nanopb).  
Use the Nanopb binary to compile .proto files in C. (see the readme at github for more info)  

Things to note:  
Both watch app & android app need to use the same .proto files  
Please duplicate the files into the respective folders if any changes are made. (do a grep)  

Watch app's \*.proto needs extra configuration if the \*.proto includes variable length fields 
like strings, bytes or repeated fields. Specify the max number of repetitions, or max size in an accompanying \*.options file.
