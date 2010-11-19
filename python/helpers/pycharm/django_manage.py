#!/usr/bin/env python
from pycharm.fix_getpass import fixGetpass
from pycharm import django_settings
import os
from runpy import run_module

manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
if not manage_file:
    manage_file = 'manage'

if __name__ == "__main__":
    fixGetpass()
    run_module(manage_file, None, '__main__')
