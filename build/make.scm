#!/usr/bin/guile
!#

(import (sph) (sph one))
;use 3 on final build
(define optimise 3)
(system "mkdir -p temp")
(define source-files (list (list "source/" "main" "file-handles") (list "lib/sph/" "sph")))
(define formatting (not (null? (tail (program-arguments)))))
;sc compile

(each
  (l (path+filenames)
    (let (path (first path+filenames))
      (each
        (l (name) (system* "sc" (string-append path name ".sc") (string-append "temp/" name ".c"))
          (if formatting (system* "astyle" (string-append "temp/" name ".c"))))
        (tail path+filenames))))
  source-files)

(exit
  (system
    (string-join
      (list "gcc -std=gnu99 -Wall -Werror -Wfatal-errors"
        (string-append "-O" (number->string optimise)) "-shared -fPIC -I/usr/include/guile/2.0"
        "-lfuse $(guile-config link) -o temp/libguile-fuse.so temp/main.c -Wl,--version-script=build/export && chmod 755 -R temp"))))