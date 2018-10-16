status: worked but the code is old and needs update. some things that need to be done:
* sph-sc has been updated and the sc code uses old syntax
* include a ready c compiled and formatted version

# dependencies
* run-time
  * guile
  * sph-lib
* compile
  * sph-sc
  * bash/dash

# installation
    ./exec/compile
    su root
    ./exec/install

  or set the installation prefix (the default is /usr)

    ./exec/install /usr/local
