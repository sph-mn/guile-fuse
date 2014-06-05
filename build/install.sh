prefix=/usr
install -d $prefix/lib
install -d $prefix/share/guile/site
install temp/libguile-fuse.so $prefix/lib
install source/fuse.scm $prefix/share/guile/site
