guile bindings to fuse - filesystems in userspace

status: worked and tested quite heavily in the past but it needs to be tested again.

see also [fuse homepage](http://fuse.sourceforge.net/)

# features
* supports almost all [fuse operations](http://fuse.sourceforge.net/doxygen/structfuse__operations.html), but not all fully. the only unsupported operations are: read-buf and write-buf (no idea how to make use of their possible benefits).
* supports using any scheme data-type as file- or directory-handle
* supports the default fuse command-line options
* to define a fuse operation, define a procedure named like the c fuse operation with an additional prefix "gf-"
* currently does not support the multithreading option of fuse (how to initialise guile to do this?)
* operations where not all fuse arguments are passed: fsync fsyncdir init ioctl listxattr lock mknod poll readlink (just not implemented)

# example
```
(import (fuse))

(define (gf-getattr path)
  (cond
    ((equal? "/" path) (quote ((type . directory) (perm . 511))))
    ((equal? "/testfile" path) (quote ((type . regular) (perm . 511) (size . 5))))
    (else #f)))

(define (gf-readdir path offset handle) (if (< offset 4) "testfile" #t))

(define (gf-read path size offset handle)
  (u8-list->bytevector (list 99 97 102 195 169)))

(define (gf-write path data offset handle)
  (bytevector-length data))

(gf-start mount-path (list "-f"))
```

# setup
## dependencies
* run-time
  * [guile](https://www.gnu.org/software/guile) 2+
  * [fuse](http://fuse.sourceforge.net/)
* quick build
  * gcc and shell for the provided compile script
* development build
  * [sc](https://github.com/sph-mn/sph-sc)
  * clang-format (part of cmake)

## installation
```
./exe/compile-c
./exe/install --help
su root
./exe/install
```

# installed files
* /usr/lib/libguile-fuse.so
* /usr/share/guile/site/fuse.scm

# possible enhancements
* maybe another way to bind procedures to fuse operations. perhaps a hashtable or procedure with keyword arguments
* adding the missing features mentioned above
