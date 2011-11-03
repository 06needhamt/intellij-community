#!/usr/bin/env python
from pycharm.fix_getpass import fixGetpass
from pycharm import django_settings
from django.core.management import execute_manager, setup_environ

import os
manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
if not manage_file:
    manage_file = 'manage'

if __name__ == "__main__":
  setup_environ(django_settings)
  from django.conf import settings
  settings.DATABASES # just to initialize django lazy settings correctly
  try:
    __import__(manage_file)
  except ImportError:
    print ("There is no such manage file " + str(manage_file))
  fixGetpass()
  execute_manager(django_settings)
