(includep "errno.h")
(includep "stdio.h")
(includep "string.h")
(includep "sys/stat.h")
(includep "libguile.h")
(define-macro _FILE_OFFSET_BITS 64)
(define-macro FUSE-USE-VERSION 30)
(includep "fuse.h")
(include "sph.c")
(include "file-handles.c")
(define-macro (mode->perm mode) (bit-and mode 511))
;stores symbolic names for file types - used in gf-alist-to-stat
(define-macro (define-scm-result arg) (define scm-result SCM arg))

(define-macro (default-return default-error)
  ;if scm-val is true, return 0
  ;if scm-val is an integer, negate and return it
  ;if scm-val is false return default-error
  (return
    (if* (scm-is-integer scm-result) (scm->int32 scm-result)
      (if* (scm-is-false scm-result) default-error 0))))

(define-macro (set-stat-ele-if-exists stat-ele key-name stat-buffer alist temp-scm)
  (set temp-scm (scm-assq-ref alist (scm-from-locale-symbol key-name)))
  (if (scm-is-integer temp-scm) (struct-set (deref stat-buffer) stat-ele (scm->int temp-scm))))

(define-macro (get-stat-type v)
  (if* (scm-is-true v)
    (if* (scm-is-true (scm-eqv? v file-regular)) S-IFREG
      (if* (scm-is-true (scm-eqv? v file-directory)) S-IFDIR
        (if* (scm-is-true (scm-eqv? v file-symlink)) S-IFLNK
          (if* (scm-is-true (scm-eqv? v file-block-special)) S-IFBLK
            (if* (scm-is-true (scm-eqv? v file-char-special)) S-IFCHR
              (if* (scm-is-true (scm-eqv? v file-fifo)) S-IFIFO
                (if* (scm-is-true (scm-eqv? v file-socket)) S-IFSOCK 0)))))))
    0))

(define-macro (set-stat-info-from-alist b arg)
  ;b: stat-buffer, v: alist-value, arg: alist
  ;file mode - file type and permissions combined. example: statbuf->st-mode = S-IFDIR | 0555;
  ;set .mode
  (define v SCM (scm-assv-ref arg (scm-from-locale-symbol "mode")))
  (if (scm-is-true v) (struct-set (deref b) st-mode (scm->int v))
    (begin
      ;mode not given, create it from "type" and "perm".
      (set v (scm-assv-ref arg (scm-from-locale-symbol "type"))) (define type b16 (get-stat-type v))
      (set v (scm-assv-ref arg (scm-from-locale-symbol "perm")))
      (struct-set (deref b) st-mode (if* (scm-is-true v) (bit-or type (scm->uint16 v)) type))))
  ;set other stat information
  (set-stat-ele-if-exists st-size "size" b arg v) (set-stat-ele-if-exists st-nlink "nlink" b arg v)
  (set-stat-ele-if-exists st-uid "uid" b arg v) (set-stat-ele-if-exists st-gid "gid" b arg v)
  (set-stat-ele-if-exists st-mtime "mtime" b arg v) (set-stat-ele-if-exists st-atime "atime" b arg v)
  (set-stat-ele-if-exists st-ctime "ctime" b arg v) (set-stat-ele-if-exists st-rdev "rdev" b arg v)
  (set-stat-ele-if-exists st-blksize "blksize" b arg v)
  (set-stat-ele-if-exists st-blocks "blocks" b arg v) (return 0))

;the filesystem procedures call a scheme procedure and return a number indicating status

(define file-regular SCM
  file-directory SCM
  file-symlink SCM
  file-block-special SCM
  file-char-special SCM
  file-fifo SCM
  file-socket SCM
  gf-scm-access (static SCM)
  gf-scm-bmap (static SCM)
  gf-scm-chmod (static SCM)
  gf-scm-chown (static SCM)
  gf-scm-create (static SCM)
  gf-scm-destroy (static SCM)
  gf-scm-fgetattr (static SCM)
  gf-scm-flush (static SCM)
  gf-scm-fsync (static SCM)
  gf-scm-fsyncdir (static SCM)
  gf-scm-ftruncate (static SCM)
  gf-scm-getattr SCM
  gf-scm-getxattr (static SCM)
  gf-scm-init (static SCM)
  gf-scm-ioctl (static SCM)
  gf-scm-link (static SCM)
  gf-scm-listxattr (static SCM)
  gf-scm-lock (static SCM)
  gf-scm-mkdir (static SCM)
  gf-scm-mknod (static SCM)
  gf-scm-open (static SCM)
  gf-scm-opendir (static SCM)
  gf-scm-poll (static SCM)
  gf-scm-read (static SCM)
  gf-scm-read-direct-io (static SCM)
  gf-scm-readdir (static SCM)
  gf-scm-readdir-without-offset (static SCM)
  gf-scm-readlink (static SCM)
  gf-scm-release (static SCM)
  gf-scm-releasedir (static SCM)
  gf-scm-removexattr (static SCM)
  gf-scm-rename (static SCM)
  gf-scm-rmdir (static SCM)
  gf-scm-setxattr (static SCM)
  gf-scm-statfs (static SCM)
  gf-scm-symlink (static SCM)
  gf-scm-truncate (static SCM)
  gf-scm-unlink (static SCM) gf-scm-utimens (static SCM) gf-scm-write (static SCM))

(define-macro (getattr-process-result)
  (if (scm-is-false scm-result) (return -ENOENT)
    (if (scm-is-true (scm-list? scm-result)) (set-stat-info-from-alist stat-info scm-result)
      (if (and (scm-is-bool scm-result) (scm-is-true scm-result))
        (begin
          ;default-file on boolean true
          (struct-set (deref stat-info) st-mode (bit-or S-IFREG 511))
          (struct-set (deref stat-info) st-size 4096)))))
  (default-return -1))

(define (gf-getattr path stat-info) (b32-s (const char*) (struct stat*))
  (define-scm-result (scm-call-1 gf-scm-getattr (scm-from-locale-string path)))
  (getattr-process-result))

(define (gf-mkdir path mode) (b32-s (const char*) mode-t)
  (define-scm-result
    (scm-call-2 gf-scm-mkdir (scm-from-locale-string path) (scm-from-int (mode->perm mode))))
  (default-return -1))

(define (gf-access path mask) (b32-s (const char*) b32-s)
  (define-scm-result (scm-call-2 gf-scm-access (scm-from-locale-string path) (scm-from-int32 mask)))
  (default-return -1))

(define (gf-bmap path blocksize index) (b32-s (const char*) size-t uint64-t*)
  (define-scm-result
    (scm-call-2 gf-scm-bmap (scm-from-locale-string path) (scm-from-int32 blocksize)))
  (default-return -1))

(define (gf-chmod path mode) (b32-s (const char*) mode-t)
  (define-scm-result (scm-call-2 gf-scm-chmod (scm-from-locale-string path) (scm-from-int mode)))
  (default-return -1))

(define (gf-chown path uid gid) (b32-s (const char*) uid-t gid-t)
  (define-scm-result
    (scm-call-3 gf-scm-chown (scm-from-locale-string path) (scm-from-int uid) (scm-from-int gid)))
  (default-return -1))

(define (gf-create path mode file-info) (b32-s (const char*) mode-t (struct fuse-file-info*))
  (define-scm-result (scm-call-2 gf-scm-create (scm-from-locale-string path) (scm-from-int mode)))
  (file-handle-add-if file-info scm-result) (default-return -1))

(define (gf-destroy fuse-userdata) (b0 b0*) (scm-call-0 gf-scm-destroy))

(define (gf-fgetattr path stat-info file-info)
  (b32-s (const char*) (struct stat*) (struct fuse-file-info*)) file-handle-init
  (define-scm-result (scm_call_2 gf-scm-fgetattr (scm-from-locale-string path) file_handle))
  (getattr-process-result))

(define (gf-flush path file-info) (b32-s (const char*) (struct fuse-file-info*))
  (define-scm-result (scm-call-1 gf-scm-flush (scm-from-locale-string path))) (default-return -1))

(define (gf-fsync path datasync file-info) (b32-s (const char*) b32-s (struct fuse-file-info*))
  ;missing arguments
  file-handle-init
  (define-scm-result (scm-call-2 gf-scm-fsync (scm-from-locale-string path) file-handle))
  (default-return -1))

(define (gf-fsyncdir path datasync file-info) (b32-s (const char*) b32-s (struct fuse-file-info*))
  ;missing arguments
  file-handle-init
  (define-scm-result (scm-call-2 gf-scm-fsyncdir (scm-from-locale-string path) file-handle))
  (default-return -1))

(define (gf-ftruncate path offset file-info) (b32-s (const char*) off-t (struct fuse-file-info*))
  file-handle-init
  (define-scm-result
    (scm-call-3 gf-scm-ftruncate (scm-from-locale-string path) (scm-from-int offset) file-handle))
  (default-return -1))

(define (gf-getxattr path name value size) (b32-s (const char*) (const char*) char* size-t)
  (define-scm-result
    (scm-call-4 gf-scm-getxattr (scm-from-locale-string path)
      (scm-from-locale-string name) (scm-from-locale-string value) (scm-from-size-t size)))
  (default-return -1))

(define (gf-init conn-info) (b0* (struct fuse-conn-info*))
  ;not implemented
  ;"The return value will passed in the private_data field of fuse_context to all file operations and as a parameter to the destroy() method"
  ;perhaps a string or bytevector
  (scm-call-0 gf-scm-init) (return 0))

(define (gf-ioctl path cmd arg file-info flags data)
  (b32-s (const char*) int b0* (struct fuse-file-info*) b32 b0*) file-handle-init
  ;missing arguments
  (define-scm-result
    (scm-call-4 gf-scm-ioctl (scm-from-locale-string path)
      (scm-from-int cmd) file-handle (scm-from-int flags)))
  (default-return -1))

(define (gf-link path target-path) (b32-s (const char*) (const char*))
  (define-scm-result
    (scm-call-2 gf-scm-link (scm-from-locale-string path) (scm-from-locale-string target-path)))
  (default-return -1))

(define (gf-listxattr path list size) (b32-s (const char*) char* size-t)
  ;missing arguments
  (define-scm-result
    (scm-call-2 gf-scm-listxattr (scm-from-locale-string path) (scm-from-size-t size)))
  (default-return -1))

(define (gf-lock path file-info cmd flock)
  (b32-s (const char*) (struct fuse-file-info*) b32-s (struct flock*))
  ;missing arguments
  (define-scm-result (scm-call-2 gf-scm-lock (scm-from-locale-string path) (scm-from-int cmd)))
  (default-return -1))

(define (gf-mknod path mode dev) (b32-s (const char*) mode-t dev-t)
  ;missing arguments
  (define-scm-result (scm-call-2 gf-scm-mknod (scm-from-locale-string path) (scm-from-int mode)))
  (default-return -1))

(define (gf-open path file-info) (b32-s (const char*) (struct fuse-file-info*))
  ;O_CREAT, O_EXCL and by default also O_TRUNC flags are not passed to open by fuse
  (define-scm-result
    (scm-call-2 gf-scm-open (scm-from-locale-string path)
      (scm-from-int (struct-ref (deref file-info) flags))))
  (file-handle-add-if file-info scm-result) (default-return -1))

(define (gf-opendir path file-info) (b32-s (const char*) (struct fuse-file-info*))
  (define-scm-result (scm-call-1 gf-scm-opendir (scm-from-locale-string path)))
  (file-handle-add-if file-info scm-result) (default-return -1))

(define (gf-poll path file-info poll-handle reventsp)
  (b32-s (const char*) (struct fuse_file_info*) (struct fuse-pollhandle*) unsigned*)
  ;missing arguments
  file-handle-init
  (define-scm-result (scm-call-2 gf-scm-poll (scm-from-locale-string path) file-handle))
  (default-return -1))

(define (gf-read-direct-io path buf size offset file-info)
  (b32-s (const char*) char* size-t off-t (struct fuse_file_info*)) file-handle-init
  (define scm-buf SCM (scm-c-make-bytevector size))
  (define result b32-s
    (scm->int
      (scm-call-5 gf-scm-read-direct-io (scm-from-locale-string path)
        scm-buf (scm-from-size-t size) (scm-from-int offset) file-handle)))
  (if (> result 0) (memcpy buf (SCM-BYTEVECTOR-CONTENTS scm-buf) result)) (return result))

(define (gf-read path buf size offset file-info)
  (b32-s (const char*) char* size-t off-t (struct fuse_file_info*)) file-handle-init
  (define-scm-result
    (scm-call-4 gf-scm-read (scm-from-locale-string path)
      (scm-from-size-t size) (scm-from-int offset) file-handle))
  (if (scm-is-bytevector scm-result)
    (begin (define size size-t (SCM-BYTEVECTOR-LENGTH scm-result))
      (memcpy buf (SCM-BYTEVECTOR-CONTENTS scm-result) size) (return size))
    (default-return -1)))

(define (gf-readdir path buf add-dir-entry offset file-info)
  (b32-s (const char*) b0* fuse-fill-dir-t off-t (struct fuse-file-info*)) file-handle-init
  (define-scm-result
    (scm-call-3 gf-scm-readdir (scm-from-locale-string path) (scm-from-int offset) file-handle))
  (if (scm-is-string scm-result)
    (begin (define file-name char* (scm->locale-string scm-result))
      (add-dir-entry buf file-name 0 (+ 1 offset)) (free file-name) (return 0))
    (default-return -1)))

(define (gf-readdir-without-offset path buf add-dir-entry offset file-info)
  (b32-s (const char*) b0* fuse-fill-dir-t off-t (struct fuse-file-info*)) file-handle-init
  (define-scm-result
    (scm-call-2 gf-scm-readdir-without-offset (scm-from-locale-string path) file-handle))
  (if (scm-is-true (scm-list? scm-result))
    (begin
      (while (not (scm-is-null scm-result))
        (define file-name char* (scm->locale-string (scm-first scm-result)))
        (add-dir-entry buf file-name 0 0) (free file-name) (set scm-result (scm-tail scm-result)))
      (return 0))
    (default-return -1)))

(define (gf-rmdir path) (b32-s (const char*))
  (define-scm-result (scm-call-1 gf-scm-rmdir (scm-from-locale-string path))) (default-return -1))

(define (gf-readlink path buf size) (b32-s (const char*) char* size-t)
  ;missing arguments
  (define-scm-result (scm-call-1 gf-scm-readlink (scm-from-locale-string path))) (default-return -1))

(define (gf-release path file-info) (b32-s (const char*) (struct fuse-file-info*))
  file-handle-init
  (define-scm-result (scm-call-2 gf-scm-release (scm-from-locale-string path) file-handle))
  (if (and (scm-is-bool scm-result) (scm-is-true scm-result))
    (file-handle-remove-if file-info file-handle))
  (default-return -1))

(define (gf-releasedir path file-info) (b32-s (const char*) (struct fuse-file-info*))
  file-handle-init
  (define-scm-result (scm-call-2 gf-scm-releasedir (scm-from-locale-string path) file-handle))
  (if (scm-is-true scm-result) (file-handle-remove-if file-info file-handle)) (default-return -1))

(define (gf-removexattr path name) (b32-s (const char*) (const char*))
  (define-scm-result
    (scm-call-2 gf-scm-removexattr (scm-from-locale-string path) (scm-from-locale-string name)))
  (default-return -1))

(define (gf-rename path target-path) (b32-s (const char*) (const char*))
  (define-scm-result
    (scm-call-2 gf-scm-rename (scm-from-locale-string path) (scm-from-locale-string target-path)))
  (default-return -1))

(define (gf-setxattr path name value size flags)
  (b32-s (const char*) (const char*) (const char*) size-t b32-s)
  (define-scm-result
    (scm-call-5 gf-scm-setxattr (scm-from-locale-string path)
      (scm-from-locale-string name) (scm-from-locale-string value)
      (scm-from-size-t size) (scm-from-int flags)))
  (default-return -1))

(define (gf-statfs path statvfsbuf) (b32-s (const char*) (struct statvfs*))
  (define-scm-result (scm-call-1 gf-scm-statfs (scm-from-locale-string path)))
  (if (scm-is-true (scm-list? scm-result))
    (let-macro
      ( (set-statfs statfs-ele key-name)
        (begin (set v (scm-assv-ref scm-result (scm-from-locale-symbol key-name)))
          (if (not (scm-is-false v)) (struct-set (deref statvfsbuf) statfs-ele (scm->int64 v)))))
      (define v SCM) (set-statfs f-bsize "bsize")
      (set-statfs f-blocks "blocks") (set-statfs f-bfree "bfree")
      (set-statfs f-bavail "bavail") (set-statfs f-files "files")
      (set-statfs f-ffree "ffree") (set-statfs f-namemax "namemax") (return 0))
    (default-return -1)))

(define (gf-symlink path target-path) (b32-s (const char*) (const char*))
  (define-scm-result
    (scm-call-2 gf-scm-symlink (scm-from-locale-string path) (scm-from-locale-string target-path)))
  (default-return -1))

(define (gf-truncate path offset) (b32-s (const char*) off-t)
  (define-scm-result
    (scm-call-2 gf-scm-truncate (scm-from-locale-string path) (scm-from-int offset)))
  (default-return -1))

(define (gf-unlink path) (b32-s (const char*))
  (define-scm-result (scm-call-1 gf-scm-unlink (scm-from-locale-string path))) (default-return -1))

(define (gf-utimens path tv[2]) (b32-s (const char*) (const struct timespec))
  (define-scm-result
    (scm-call-5 gf-scm-utimens (scm-from-locale-string path)
      (scm-from-int (struct-ref (deref tv) tv-sec))
      (scm-from-int (struct-ref (deref (+ 1 tv)) tv-sec))
      (scm-from-int (struct-ref (deref tv) tv-nsec))
      (scm-from-int (struct-ref (deref (+ 1 tv)) tv-nsec))))
  (default-return -1))

(define (gf-write path data size offset file-info)
  (b32-s (const char*) (const char*) size-t off-t (struct fuse-file-info*)) file-handle-init
  (define scm-data SCM (scm-c-make-bytevector size))
  (memcpy (SCM-BYTEVECTOR-CONTENTS scm-data) data size)
  (define-scm-result
    (scm-call-4 gf-scm-write (scm-from-locale-string path)
      scm-data (scm-from-int offset) file-handle))
  (default-return -1))

(define-macro (set-file-type-symbols) (set file-regular (scm-from-locale-symbol "regular"))
  (set file-directory (scm-from-locale-symbol "directory"))
  (set file-symlink (scm-from-locale-symbol "symlink"))
  (set file-block-special (scm-from-locale-symbol "block-special"))
  (set file-char-special (scm-from-locale-symbol "char-special"))
  (set file-fifo (scm-from-locale-symbol "fifo")) (set file-socket (scm-from-locale-symbol "socket")))

(define-macro (link-procedure name gf-procedure string-name gf-scm-procedure)
  ;set a fuse operation if a corresponding scm-procedure is defined in the current module
  (set gf-scm-procedure (scm-module-variable module (scm-from-locale-symbol string-name)))
  (if (and (scm-is-true gf-scm-procedure) (scm-is-true (scm-variable-bound? gf-scm-procedure)))
    (begin (set gf-scm-procedure (scm-variable-ref gf-scm-procedure))
      (set fuse-operations.name gf-procedure))))

(define-macro (define-and-set-fuse-operations module)
  (define fuse-operations (static struct fuse-operations))
  (link-procedure getattr gf-getattr "gf-getattr" gf-scm-getattr)
  (link-procedure readdir gf-readdir "gf-readdir" gf-scm-readdir)
  (link-procedure readdir gf-readdir-without-offset
    "gf-readdir-without-offset" gf-scm-readdir-without-offset)
  (link-procedure mkdir gf-mkdir "gf-mkdir" gf-scm-mkdir)
  (link-procedure read gf-read "gf-read" gf-scm-read)
  (link-procedure read gf-read "gf-read-direct-io" gf-scm-read-direct-io)
  (link-procedure rmdir gf-rmdir "gf-rmdir" gf-scm-rmdir)
  (link-procedure unlink gf-unlink "gf-unlink" gf-scm-unlink)
  (link-procedure write gf-write "gf-write" gf-scm-write)
  (link-procedure statfs gf-statfs "gf-statfs" gf-scm-statfs)
  (link-procedure create gf-create "gf-create" gf-scm-create)
  (link-procedure chmod gf-chmod "gf-chmod" gf-scm-chmod)
  (link-procedure chown gf-chown "gf-chown" gf-scm-chown)
  (link-procedure access gf-access "gf-access" gf-scm-access)
  (link-procedure bmap gf-bmap "gf-bmap" gf-scm-bmap)
  (link-procedure setxattr gf-setxattr "gf-setxattr" gf-scm-setxattr)
  (link-procedure symlink gf-symlink "gf-symlink" gf-scm-symlink)
  (link-procedure utimens gf-utimens "gf-utimens" gf-scm-utimens)
  (link-procedure destroy gf-destroy "gf-destroy" gf-scm-destroy)
  (link-procedure fgetattr gf-fgetattr "gf-fgetattr" gf-scm-fgetattr)
  (link-procedure flush gf-flush "gf-flush" gf-scm-flush)
  (link-procedure fsync gf-fsync "gf-fsync" gf-scm-fsync)
  (link-procedure fsyncdir gf-fsyncdir "gf-fsyncdir" gf-scm-fsyncdir)
  (link-procedure ftruncate gf-ftruncate "gf-ftruncate" gf-scm-ftruncate)
  (link-procedure getxattr gf-getxattr "gf-getxattr" gf-scm-getxattr)
  (link-procedure init gf-init "gf-init" gf-scm-init)
  (link-procedure ioctl gf-ioctl "gf-ioctl" gf-scm-ioctl)
  (link-procedure link gf-link "gf-link" gf-scm-link)
  (link-procedure listxattr gf-listxattr "gf-listxattr" gf-scm-listxattr)
  (link-procedure lock gf-lock "gf-lock" gf-scm-lock)
  (link-procedure mknod gf-mknod "gf-mknod" gf-scm-mknod)
  (link-procedure open gf-open "gf-open" gf-scm-open)
  (link-procedure opendir gf-opendir "gf-opendir" gf-scm-opendir)
  (link-procedure poll gf-poll "gf-poll" gf-scm-poll)
  (link-procedure readlink gf-readlink "gf-readlink" gf-scm-readlink)
  (link-procedure release gf-release "gf-release" gf-scm-release)
  (link-procedure releasedir gf-releasedir "gf-releasedir" gf-scm-releasedir)
  (link-procedure removexattr gf-removexattr "gf-removexattr" gf-scm-removexattr)
  (link-procedure rename gf-rename "gf-rename" gf-scm-rename)
  (link-procedure truncate gf-truncate "gf-truncate" gf-scm-truncate))

(define (gf-start arguments) (SCM SCM)
  ;(string ...):fuse-options -> integer
  (define module SCM (scm-current-module)) (file-handles-init)
  (set-file-type-symbols) (define-and-set-fuse-operations module)
  (define arguments-count int (scm->int (scm-length arguments))) (define c-arguments char**)
  (define c-arguments-p char**)
  (if arguments-count
    (begin (set c-arguments (malloc (* (sizeof pointer) arguments-count)))
      (set c-arguments-p c-arguments)
      (while (not (scm-is-null arguments))
        (set (deref c-arguments-p) (scm->locale-string (scm-first arguments)))
        (increment-one c-arguments-p) (set arguments (scm-tail arguments))))
    (set c-arguments 0))
  (define result SCM
    (scm-from-int (fuse-main arguments-count c-arguments (address-of fuse-operations) 0)))
  (if arguments-count
    (begin (decrement-one c-arguments-p)
      (while (>= c-arguments-p c-arguments) (free (deref c-arguments-p))
        (decrement-one c-arguments-p))
      (free c-arguments)))
  (return result))

(define (init-guile-fuse) b0 (scm-c-define-gsubr "primitive-gf-start" 1 0 1 gf-start))

(undefine-macro link-procedure link-procedures
  set-file-type-symbols mode->perm
  define-scm-result default-return
  file-handle-set file-handle-init
  file-handle-add-if file-handle-remove-if
  set-stat-ele-if-exists get-stat-type set-stat-info-from-alist getattr-process-result)