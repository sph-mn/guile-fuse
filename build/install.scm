#!/usr/bin/guile
!#

(define prefix
  (let ((arguments (cdr (program-arguments))))
    (if (> (length arguments) 0) (car arguments) "/usr")))

(system* "install" "-d" (string-append prefix "/lib"))
(system* "install" "-d" (string-append prefix "/share/guile/site"))
(system* "install" "temp/libguile-fuse.so" (string-append prefix "/lib"))
(system* "install" "source/fuse.scm" (string-append prefix "/share/guile/site"))