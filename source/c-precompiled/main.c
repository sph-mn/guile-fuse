#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <libguile.h>
#define _FILE_OFFSET_BITS 64
#define FUSE_USE_VERSION 30
#include <fuse.h>
/* short type names. requires >= C99 standard */
#define pointer uintptr_t
#define b0 void
#define b8 uint8_t
#define b16 uint16_t
#define b32 uint32_t
#define b64 uint64_t
#define b8_s int8_t
#define b16_s int16_t
#define b32_s int32_t
#define b64_s int64_t
#define null ((b0)(0))
#define _noalias restrict
#define _readonly const
#define increment_one(arg) arg = (1 + arg)
#define decrement_one(arg) arg = (arg - 1)
#define debug_log(format, ...) fprintf(stderr, "%s:%d " format "\n", __func__, __LINE__, __VA_ARGS__)
#define scm_first SCM_CAR
#define scm_tail SCM_CDR
#define file_handles_max 400
SCM file_handles;
/* index 0 is reserved for errors */
b64 file_handles_index = 1;
pthread_mutex_t file_handle_next_mutex;
#define file_handles_init() \
  scm_permanent_object(file_handles); \
  file_handles = scm_c_make_vector((1 + file_handles_max), SCM_BOOL_F)
b64 file_handle_next() {
  pthread_mutex_lock((&file_handle_next_mutex));
  b32 tries_left = file_handles_max;
  while ((scm_is_true((SCM_SIMPLE_VECTOR_REF(file_handles, file_handles_index))) && tries_left)) {
    file_handles_index = ((file_handles_index < file_handles_max) ? (1 + file_handles_index) : 1);
    decrement_one(tries_left);
  };
  b64 result = (tries_left ? file_handles_index : (debug_log("%s", "too many concurrently open file-handles"), 0));
  pthread_mutex_unlock((&file_handle_next_mutex));
  return (result);
};
/** get a file handle from the list of open file handles */
#define file_handle_set(file_info, handle) handle = (file_info->fh ? SCM_SIMPLE_VECTOR_REF(file_handles, (file_info->fh)) : SCM_BOOL_F)
#define file_handle_init \
  SCM file_handle; \
  file_handle_set(file_info, file_handle);
/** add a file-handle to the list of open file handles if its value is not a boolean */
#define file_handle_add_if(file_info, handle) \
  if (scm_is_false((scm_boolean_p(handle)))) { \
    b64 index = file_handle_next(); \
    if (index) { \
      SCM_SIMPLE_VECTOR_SET(file_handles, index, handle); \
      file_info->fh = index; \
    }; \
  }
#define file_handle_remove(file_info, file_handle) \
  if (file_info->fh) { \
    SCM_SIMPLE_VECTOR_SET(file_handles, (file_info->fh), SCM_BOOL_F); \
    file_info->fh = 0; \
  }
#define mode_to_perm(mode) (mode & 511)
#define define_scm_result(arg) SCM scm_result = arg
/** if scm-val is true, return 0
     if scm-val is an integer, convert and return it
     if scm-val is false return default-error */
#define default_return(default_error) return ((scm_is_integer(scm_result) ? scm_to_int32(scm_result) : (scm_is_false(scm_result) ? default_error : 0)))
#define set_stat_ele_if_exists(stat_ele, key_name, stat_buffer, alist, temp_scm) \
  temp_scm = scm_assq_ref(alist, (scm_from_locale_symbol(key_name))); \
  if (scm_is_integer(temp_scm)) { \
    stat_buffer->stat_ele = scm_to_int(temp_scm); \
  }
#define get_stat_type(v) (scm_is_true(v) ? (scm_is_true((scm_eqv_p(v, file_regular))) ? S_IFREG : (scm_is_true((scm_eqv_p(v, file_directory))) ? S_IFDIR : (scm_is_true((scm_eqv_p(v, file_symlink))) ? S_IFLNK : (scm_is_true((scm_eqv_p(v, file_block_special))) ? S_IFBLK : (scm_is_true((scm_eqv_p(v, file_char_special))) ? S_IFCHR : (scm_is_true((scm_eqv_p(v, file_fifo))) ? S_IFIFO : (scm_is_true((scm_eqv_p(v, file_socket))) ? S_IFSOCK : 0))))))) : 0)
/** b: stat-buffer, v: alist-value, arg: alist
    file mode - file type and permissions combined. example: statbuf->st-mode = S-IFDIR | 0555;
    set .mode */
#define set_stat_info_from_alist(b, arg) \
  SCM v = scm_assv_ref(arg, (scm_from_locale_symbol("mode"))); \
  if (scm_is_true(v)) { \
    b->st_mode = scm_to_int(v); \
  } else { \
    v = scm_assv_ref(arg, (scm_from_locale_symbol("type"))); \
    b16 type = get_stat_type(v); \
    v = scm_assv_ref(arg, (scm_from_locale_symbol("perm"))); \
    b->st_mode = (scm_is_true(v) ? (type | scm_to_uint16(v)) : type); \
  }; \
  set_stat_ele_if_exists(st_size, "size", b, arg, v); \
  set_stat_ele_if_exists(st_nlink, "nlink", b, arg, v); \
  set_stat_ele_if_exists(st_uid, "uid", b, arg, v); \
  set_stat_ele_if_exists(st_gid, "gid", b, arg, v); \
  set_stat_ele_if_exists(st_mtime, "mtime", b, arg, v); \
  set_stat_ele_if_exists(st_atime, "atime", b, arg, v); \
  set_stat_ele_if_exists(st_ctime, "ctime", b, arg, v); \
  set_stat_ele_if_exists(st_rdev, "rdev", b, arg, v); \
  set_stat_ele_if_exists(st_blksize, "blksize", b, arg, v); \
  set_stat_ele_if_exists(st_blocks, "blocks", b, arg, v); \
  return (0)
/* the filesystem procedures call a scheme procedure and return a number indicating status */
SCM file_regular;
SCM file_directory;
SCM file_symlink;
SCM file_block_special;
SCM file_char_special;
SCM file_fifo;
SCM file_socket;
static SCM gf_scm_access;
static SCM gf_scm_bmap;
static SCM gf_scm_chmod;
static SCM gf_scm_chown;
static SCM gf_scm_create;
static SCM gf_scm_destroy;
static SCM gf_scm_fgetattr;
static SCM gf_scm_flush;
static SCM gf_scm_fsync;
static SCM gf_scm_fsyncdir;
static SCM gf_scm_ftruncate;
SCM gf_scm_getattr;
static SCM gf_scm_getxattr;
static SCM gf_scm_init;
static SCM gf_scm_ioctl;
static SCM gf_scm_link;
static SCM gf_scm_listxattr;
static SCM gf_scm_lock;
static SCM gf_scm_mkdir;
static SCM gf_scm_mknod;
static SCM gf_scm_open;
static SCM gf_scm_opendir;
static SCM gf_scm_poll;
static SCM gf_scm_read;
static SCM gf_scm_read_direct_io;
static SCM gf_scm_readdir;
static SCM gf_scm_readdir_without_offset;
static SCM gf_scm_readlink;
static SCM gf_scm_release;
static SCM gf_scm_releasedir;
static SCM gf_scm_removexattr;
static SCM gf_scm_rename;
static SCM gf_scm_rmdir;
static SCM gf_scm_setxattr;
static SCM gf_scm_statfs;
static SCM gf_scm_symlink;
static SCM gf_scm_truncate;
static SCM gf_scm_unlink;
static SCM gf_scm_utimens;
static SCM gf_scm_write;
#define getattr_process_result() \
  if (scm_is_false(scm_result)) { \
    return ((ENOENT)); \
  } else { \
    if (scm_is_true((scm_list_p(scm_result)))) { \
      set_stat_info_from_alist(stat_info, scm_result); \
    } else { \
      if (scm_is_bool(scm_result) && scm_is_true(scm_result)) { \
        /* default-file on boolean true */ \
        stat_info->st_mode = (S_IFREG | 511); \
        stat_info->st_size = 4096; \
      }; \
    }; \
  }; \
  default_return(-1)
b32_s gf_getattr(const char* path, struct stat* stat_info) {
  define_scm_result((scm_call_1(gf_scm_getattr, (scm_from_locale_string(path)))));
  getattr_process_result();
};
b32_s gf_mkdir(const char* path, mode_t mode) {
  define_scm_result((scm_call_2(gf_scm_mkdir, (scm_from_locale_string(path)), (scm_from_int((mode_to_perm(mode)))))));
  default_return(-1);
};
b32_s gf_access(const char* path, b32_s mask) {
  define_scm_result((scm_call_2(gf_scm_access, (scm_from_locale_string(path)), (scm_from_int32(mask)))));
  default_return(-1);
};
b32_s gf_bmap(const char* path, size_t blocksize, uint64_t* index) {
  define_scm_result((scm_call_2(gf_scm_bmap, (scm_from_locale_string(path)), (scm_from_int32(blocksize)))));
  default_return(-1);
};
b32_s gf_chmod(const char* path, mode_t mode) {
  define_scm_result((scm_call_2(gf_scm_chmod, (scm_from_locale_string(path)), (scm_from_int(mode)))));
  default_return(-1);
};
b32_s gf_chown(const char* path, uid_t uid, gid_t gid) {
  define_scm_result((scm_call_3(gf_scm_chown, (scm_from_locale_string(path)), (scm_from_int(uid)), (scm_from_int(gid)))));
  default_return(-1);
};
b32_s gf_create(const char* path, mode_t mode, struct fuse_file_info* file_info) {
  define_scm_result((scm_call_2(gf_scm_create, (scm_from_locale_string(path)), (scm_from_int(mode)))));
  file_handle_add_if(file_info, scm_result);
  default_return(-1);
};
b0 gf_destroy(b0* fuse_userdata) { scm_call_0(gf_scm_destroy); };
b32_s gf_fgetattr(const char* path, struct stat* stat_info, struct fuse_file_info* file_info) {
  file_handle_init;
  define_scm_result((scm_call_2(gf_scm_fgetattr, (scm_from_locale_string(path)), file_handle)));
  getattr_process_result();
};
b32_s gf_flush(const char* path, struct fuse_file_info* file_info) {
  define_scm_result((scm_call_1(gf_scm_flush, (scm_from_locale_string(path)))));
  default_return(-1);
};
b32_s gf_fsync(const char* path, b32_s datasync, struct fuse_file_info* file_info) {
  /* missing arguments */
  file_handle_init;
  define_scm_result((scm_call_2(gf_scm_fsync, (scm_from_locale_string(path)), file_handle)));
  default_return(-1);
};
b32_s gf_fsyncdir(const char* path, b32_s datasync, struct fuse_file_info* file_info) {
  /* missing arguments */
  file_handle_init;
  define_scm_result((scm_call_2(gf_scm_fsyncdir, (scm_from_locale_string(path)), file_handle)));
  default_return(-1);
};
b32_s gf_ftruncate(const char* path, off_t offset, struct fuse_file_info* file_info) {
  file_handle_init;
  define_scm_result((scm_call_3(gf_scm_ftruncate, (scm_from_locale_string(path)), (scm_from_int(offset)), file_handle)));
  default_return(-1);
};
b32_s gf_getxattr(const char* path, const char* name, char* value, size_t size) {
  define_scm_result((scm_call_4(gf_scm_getxattr, (scm_from_locale_string(path)), (scm_from_locale_string(name)), (scm_from_locale_string(value)), (scm_from_size_t(size)))));
  default_return(-1);
};
/** not implemented
  "The return value will passed in the private_data field of fuse_context to all file operations and as a parameter to the destroy() method."
  perhaps a string or bytevector */
b0* gf_init(struct fuse_conn_info* conn_info) {
  scm_call_0(gf_scm_init);
  return (0);
};
b32_s gf_ioctl(const char* path, int cmd, b0* arg, struct fuse_file_info* file_info, b32 flags, b0* data) {
  file_handle_init;
  /* missing arguments */
  define_scm_result((scm_call_4(gf_scm_ioctl, (scm_from_locale_string(path)), (scm_from_int(cmd)), file_handle, (scm_from_int(flags)))));
  default_return(-1);
};
b32_s gf_link(const char* path, const char* target_path) {
  define_scm_result((scm_call_2(gf_scm_link, (scm_from_locale_string(path)), (scm_from_locale_string(target_path)))));
  default_return(-1);
};
b32_s gf_listxattr(const char* path, char* list, size_t size) {
  /* missing arguments */
  define_scm_result((scm_call_2(gf_scm_listxattr, (scm_from_locale_string(path)), (scm_from_size_t(size)))));
  default_return(-1);
};
b32_s gf_lock(const char* path, struct fuse_file_info* file_info, b32_s cmd, struct flock* flock) {
  /* missing arguments */
  define_scm_result((scm_call_2(gf_scm_lock, (scm_from_locale_string(path)), (scm_from_int(cmd)))));
  default_return(-1);
};
b32_s gf_mknod(const char* path, mode_t mode, dev_t dev) {
  define_scm_result((scm_call_2(gf_scm_mknod, (scm_from_locale_string(path)), (scm_from_int(mode)))));
  default_return(-1);
};
/** O_CREAT, O_EXCL and by default also O_TRUNC flags are not passed to open by fuse */
b32_s gf_open(const char* path, struct fuse_file_info* file_info) {
  define_scm_result((scm_call_2(gf_scm_open, (scm_from_locale_string(path)), (scm_from_int((file_info->flags))))));
  file_handle_add_if(file_info, scm_result);
  default_return(-1);
};
b32_s gf_opendir(const char* path, struct fuse_file_info* file_info) {
  define_scm_result((scm_call_1(gf_scm_opendir, (scm_from_locale_string(path)))));
  file_handle_add_if(file_info, scm_result);
  default_return(-1);
};
b32_s gf_poll(const char* path, struct fuse_file_info* file_info, struct fuse_pollhandle* poll_handle, unsigned* reventsp) {
  /* missing arguments */
  file_handle_init;
  define_scm_result((scm_call_2(gf_scm_poll, (scm_from_locale_string(path)), file_handle)));
  default_return(-1);
};
b32_s gf_read_direct_io(const char* path, char* buf, size_t size, off_t offset, struct fuse_file_info* file_info) {
  file_handle_init;
  SCM scm_buf = scm_c_make_bytevector(size);
  b32_s result = scm_to_int((scm_call_5(gf_scm_read_direct_io, (scm_from_locale_string(path)), scm_buf, (scm_from_size_t(size)), (scm_from_int(offset)), file_handle)));
  if (result > 0) {
    memcpy(buf, (SCM_BYTEVECTOR_CONTENTS(scm_buf)), result);
  };
  return (result);
};
b32_s gf_read(const char* path, char* buf, size_t size, off_t offset, struct fuse_file_info* file_info) {
  file_handle_init;
  define_scm_result((scm_call_4(gf_scm_read, (scm_from_locale_string(path)), (scm_from_size_t(size)), (scm_from_int(offset)), file_handle)));
  if (scm_is_bytevector(scm_result)) {
    size_t size = SCM_BYTEVECTOR_LENGTH(scm_result);
    memcpy(buf, (SCM_BYTEVECTOR_CONTENTS(scm_result)), size);
    return (size);
  } else {
    default_return(-1);
  };
};
b32_s gf_readdir(const char* path, b0* buf, fuse_fill_dir_t add_dir_entry, off_t offset, struct fuse_file_info* file_info) {
  file_handle_init;
  define_scm_result((scm_call_3(gf_scm_readdir, (scm_from_locale_string(path)), (scm_from_int(offset)), file_handle)));
  if (scm_is_string(scm_result)) {
    char* file_name = scm_to_locale_string(scm_result);
    add_dir_entry(buf, file_name, 0, (1 + offset));
    free(file_name);
    return (0);
  } else {
    default_return(-1);
  };
};
b32_s gf_readdir_without_offset(const char* path, b0* buf, fuse_fill_dir_t add_dir_entry, off_t offset, struct fuse_file_info* file_info) {
  file_handle_init;
  define_scm_result((scm_call_2(gf_scm_readdir_without_offset, (scm_from_locale_string(path)), file_handle)));
  if (scm_is_true((scm_list_p(scm_result)))) {
    while (!scm_is_null(scm_result)) {
      char* file_name = scm_to_locale_string((scm_first(scm_result)));
      if (add_dir_entry(buf, file_name, 0, 0)) {
        free(file_name);
        break;
      };
      free(file_name);
      scm_result = scm_tail(scm_result);
    };
    return (0);
  } else {
    default_return(-1);
  };
};
b32_s gf_rmdir(const char* path) {
  define_scm_result((scm_call_1(gf_scm_rmdir, (scm_from_locale_string(path)))));
  default_return(-1);
};
b32_s gf_readlink(const char* path, char* buf, size_t size) {
  /* missing arguments */
  define_scm_result((scm_call_1(gf_scm_readlink, (scm_from_locale_string(path)))));
  default_return(-1);
};
b32_s gf_release(const char* path, struct fuse_file_info* file_info) {
  file_handle_init;
  define_scm_result((scm_call_2(gf_scm_release, (scm_from_locale_string(path)), file_handle)));
  if (scm_is_bool(scm_result) && scm_is_true(scm_result)) {
    file_handle_remove(file_info, file_handle);
  };
  default_return(-1);
};
b32_s gf_releasedir(const char* path, struct fuse_file_info* file_info) {
  file_handle_init;
  define_scm_result((scm_call_2(gf_scm_releasedir, (scm_from_locale_string(path)), file_handle)));
  if (scm_is_true(scm_result)) {
    file_handle_remove(file_info, file_handle);
  };
  default_return(-1);
};
b32_s gf_removexattr(const char* path, const char* name) {
  define_scm_result((scm_call_2(gf_scm_removexattr, (scm_from_locale_string(path)), (scm_from_locale_string(name)))));
  default_return(-1);
};
b32_s gf_rename(const char* path, const char* target_path) {
  define_scm_result((scm_call_2(gf_scm_rename, (scm_from_locale_string(path)), (scm_from_locale_string(target_path)))));
  default_return(-1);
};
b32_s gf_setxattr(const char* path, const char* name, const char* value, size_t size, b32_s flags) {
  define_scm_result((scm_call_5(gf_scm_setxattr, (scm_from_locale_string(path)), (scm_from_locale_string(name)), (scm_from_locale_string(value)), (scm_from_size_t(size)), (scm_from_int(flags)))));
  default_return(-1);
};
b32_s gf_statfs(const char* path, struct statvfs* statvfsbuf) {
  define_scm_result((scm_call_1(gf_scm_statfs, (scm_from_locale_string(path)))));
  if (scm_is_true((scm_list_p(scm_result)))) {
#define set_statfs(statfs_ele, key_name) \
  v = scm_assv_ref(scm_result, (scm_from_locale_symbol(key_name))); \
  if (!scm_is_false(v)) { \
    statvfsbuf->statfs_ele = scm_to_int64(v); \
  }
    SCM v;
    set_statfs(f_bsize, "bsize");
    set_statfs(f_blocks, "blocks");
    set_statfs(f_bfree, "bfree");
    set_statfs(f_bavail, "bavail");
    set_statfs(f_files, "files");
    set_statfs(f_ffree, "ffree");
    set_statfs(f_namemax, "namemax");
    return (0);
#undef set_statfs
  } else {
    default_return(-1);
  };
};
b32_s gf_symlink(const char* path, const char* target_path) {
  define_scm_result((scm_call_2(gf_scm_symlink, (scm_from_locale_string(path)), (scm_from_locale_string(target_path)))));
  default_return(-1);
};
b32_s gf_truncate(const char* path, off_t offset) {
  define_scm_result((scm_call_2(gf_scm_truncate, (scm_from_locale_string(path)), (scm_from_int(offset)))));
  default_return(-1);
};
b32_s gf_unlink(const char* path) {
  define_scm_result((scm_call_1(gf_scm_unlink, (scm_from_locale_string(path)))));
  default_return(-1);
};
b32_s gf_utimens(const char* path, const struct timespec tv[2]) {
  define_scm_result((scm_call_5(gf_scm_utimens, (scm_from_locale_string(path)), (scm_from_int((tv->tv_sec))), (scm_from_int(((1 + tv)->tv_sec))), (scm_from_int((tv->tv_nsec))), (scm_from_int(((1 + tv)->tv_nsec))))));
  default_return(-1);
};
b32_s gf_write(const char* path, const char* data, size_t size, off_t offset, struct fuse_file_info* file_info) {
  file_handle_init;
  SCM scm_data = scm_c_make_bytevector(size);
  memcpy((SCM_BYTEVECTOR_CONTENTS(scm_data)), data, size);
  define_scm_result((scm_call_4(gf_scm_write, (scm_from_locale_string(path)), scm_data, (scm_from_int(offset)), file_handle)));
  default_return(-1);
};
#define set_file_type_symbols() \
  file_regular = scm_from_locale_symbol("regular"); \
  file_directory = scm_from_locale_symbol("directory"); \
  file_symlink = scm_from_locale_symbol("symlink"); \
  file_block_special = scm_from_locale_symbol("block-special"); \
  file_char_special = scm_from_locale_symbol("char-special"); \
  file_fifo = scm_from_locale_symbol("fifo"); \
  file_socket = scm_from_locale_symbol("socket")
/** set a fuse operation if a corresponding scm-procedure is defined in the current module */
#define link_procedure(name, gf_procedure, string_name, gf_scm_procedure) \
  gf_scm_procedure = scm_module_variable(module, (scm_from_locale_symbol(string_name))); \
  if (scm_is_true(gf_scm_procedure) && scm_is_true((scm_variable_bound_p(gf_scm_procedure)))) { \
    gf_scm_procedure = scm_variable_ref(gf_scm_procedure); \
    fuse_operations.name = gf_procedure; \
  }
#define define_and_set_fuse_operations(module) \
  static struct fuse_operations fuse_operations; \
  link_procedure(getattr, gf_getattr, "gf-getattr", gf_scm_getattr); \
  link_procedure(readdir, gf_readdir, "gf-readdir", gf_scm_readdir); \
  link_procedure(readdir, gf_readdir_without_offset, "gf-readdir-without-offset", gf_scm_readdir_without_offset); \
  link_procedure(mkdir, gf_mkdir, "gf-mkdir", gf_scm_mkdir); \
  link_procedure(read, gf_read, "gf-read", gf_scm_read); \
  link_procedure(read, gf_read, "gf-read-direct-io", gf_scm_read_direct_io); \
  link_procedure(rmdir, gf_rmdir, "gf-rmdir", gf_scm_rmdir); \
  link_procedure(unlink, gf_unlink, "gf-unlink", gf_scm_unlink); \
  link_procedure(write, gf_write, "gf-write", gf_scm_write); \
  link_procedure(statfs, gf_statfs, "gf-statfs", gf_scm_statfs); \
  link_procedure(create, gf_create, "gf-create", gf_scm_create); \
  link_procedure(chmod, gf_chmod, "gf-chmod", gf_scm_chmod); \
  link_procedure(chown, gf_chown, "gf-chown", gf_scm_chown); \
  link_procedure(access, gf_access, "gf-access", gf_scm_access); \
  link_procedure(bmap, gf_bmap, "gf-bmap", gf_scm_bmap); \
  link_procedure(setxattr, gf_setxattr, "gf-setxattr", gf_scm_setxattr); \
  link_procedure(symlink, gf_symlink, "gf-symlink", gf_scm_symlink); \
  link_procedure(utimens, gf_utimens, "gf-utimens", gf_scm_utimens); \
  link_procedure(destroy, gf_destroy, "gf-destroy", gf_scm_destroy); \
  link_procedure(fgetattr, gf_fgetattr, "gf-fgetattr", gf_scm_fgetattr); \
  link_procedure(flush, gf_flush, "gf-flush", gf_scm_flush); \
  link_procedure(fsync, gf_fsync, "gf-fsync", gf_scm_fsync); \
  link_procedure(fsyncdir, gf_fsyncdir, "gf-fsyncdir", gf_scm_fsyncdir); \
  link_procedure(ftruncate, gf_ftruncate, "gf-ftruncate", gf_scm_ftruncate); \
  link_procedure(getxattr, gf_getxattr, "gf-getxattr", gf_scm_getxattr); \
  link_procedure(init, gf_init, "gf-init", gf_scm_init); \
  link_procedure(ioctl, gf_ioctl, "gf-ioctl", gf_scm_ioctl); \
  link_procedure(link, gf_link, "gf-link", gf_scm_link); \
  link_procedure(listxattr, gf_listxattr, "gf-listxattr", gf_scm_listxattr); \
  link_procedure(lock, gf_lock, "gf-lock", gf_scm_lock); \
  link_procedure(mknod, gf_mknod, "gf-mknod", gf_scm_mknod); \
  link_procedure(open, gf_open, "gf-open", gf_scm_open); \
  link_procedure(opendir, gf_opendir, "gf-opendir", gf_scm_opendir); \
  link_procedure(poll, gf_poll, "gf-poll", gf_scm_poll); \
  link_procedure(readlink, gf_readlink, "gf-readlink", gf_scm_readlink); \
  link_procedure(release, gf_release, "gf-release", gf_scm_release); \
  link_procedure(releasedir, gf_releasedir, "gf-releasedir", gf_scm_releasedir); \
  link_procedure(removexattr, gf_removexattr, "gf-removexattr", gf_scm_removexattr); \
  link_procedure(rename, gf_rename, "gf-rename", gf_scm_rename); \
  link_procedure(truncate, gf_truncate, "gf-truncate", gf_scm_truncate)
/** (string ...):fuse-options -> integer */
SCM gf_start(SCM arguments, SCM module) {
  file_handles_init();
  set_file_type_symbols();
  define_and_set_fuse_operations(module);
  int arguments_count = scm_to_int((scm_length(arguments)));
  char** c_arguments;
  char** c_arguments_p;
  if (arguments_count) {
    c_arguments = malloc((sizeof(pointer) * arguments_count));
    c_arguments_p = c_arguments;
    while (!scm_is_null(arguments)) {
      *c_arguments_p = scm_to_locale_string((scm_first(arguments)));
      increment_one(c_arguments_p);
      arguments = scm_tail(arguments);
    };
  } else {
    c_arguments = 0;
  };
  SCM result = scm_from_int((fuse_main(arguments_count, c_arguments, (&fuse_operations), 0)));
  if (arguments_count) {
    decrement_one(c_arguments_p);
    while ((c_arguments_p >= c_arguments)) {
      free((*c_arguments_p));
      decrement_one(c_arguments_p);
    };
    free(c_arguments);
  };
  return (result);
};
b0 init_guile_fuse() { scm_c_define_gsubr("primitive-gf-start", 2, 0, 0, gf_start); };
#undef link_procedure
#undef link_procedures
#undef set_file_type_symbols
#undef mode_to_perm
#undef define_scm_result
#undef default_return
#undef file_handle_set
#undef file_handle_init
#undef file_handle_add_if
#undef file_handle_remove
#undef set_stat_ele_if_exists
#undef get_stat_type
#undef set_stat_info_from_alist
#undef getattr_process_result
