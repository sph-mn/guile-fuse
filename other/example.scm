#!/usr/bin/guile
!#

; this script mounts to "/tmp/fuse-example".
; exit with Ctrl+C should unmount the filesystem.
; - if you get "no such file or directory" and the filesystem is still mounted, umount the filesystem first
; - permission issues can be caused by wrong return values
; - the display of data read with "read" often depends on previously returned size values of getattr

(import (fuse) (rnrs bytevectors))
(define (log . msg) (display msg) (newline))

(define (gf-getattr path)
  (if (equal? "/" path) (quote ((type . directory) (perm . 511)))
    (if (equal? "/testfile" path) (quote ((type . regular) (perm . 511) (size . 5))) #f)))

(define (gf-readdir path offset handle) (if (< offset 4) "testfile" #t))
(define (gf-mkdir path mode) (simple-format #t "mkdir called ~A ~A\n" path (number->string 8)) #t)

(define (gf-read path size offset handle) (log "read called" handle)
  (u8-list->bytevector (quote (99 97 102 195 169))))

(define (gf-write path data offset handle) (simple-format #t "write called ~S\n" data)
  (bytevector-length data))

(define (gf-statfs path) (log "statfs called")
  (quote ((files . 1000) (ffree . 100) (bavail . 2000) (bfree . 3000) (bsize . 10) (namemax . 20))))

(define (gf-rmdir path) (log "rmdir called"))
(define (gf-unlink path) (log "unlink called"))
(define (gf-open path mode) (simple-format #t "open ~A ~A\n" path mode) (quote handle))
(define (gf-opendir path) (log "opendir called") #t)
(define (gf-truncate path offset) (log "truncate called") #t)

(let ((mount-path (string-append (dirname (tmpnam)) "/fuse-example")))
  (if (not (file-exists? mount-path)) (mkdir mount-path))
  (simple-format #t "mounting to ~A\n" mount-path) (gf-start mount-path (list "-f")))
