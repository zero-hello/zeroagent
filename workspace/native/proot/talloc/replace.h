/* ZeroAgent's Android API 31+ portability header for the unmodified talloc.c.
 * Android bionic supplies every libc function used by this translation unit;
 * no Samba libreplace replacement objects are needed or linked.
 * This header is part of the corresponding build source, not upstream talloc.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
#ifndef ZEROAGENT_TALLOC_ANDROID_REPLACE_H
#define ZEROAGENT_TALLOC_ANDROID_REPLACE_H
#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 4
#define TALLOC_BUILD_VERSION_RELEASE 3
#define HAVE_INTPTR_T 1
#define HAVE_VA_COPY 1
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define HAVE_SYS_AUXV_H 1
#define HAVE_GETAUXVAL 1
#define PRINTF_ATTRIBUTE(a,b) __attribute__((format(printf,a,b)))
#define MIN(a,b) ((a)<(b)?(a):(b))
#define MAX(a,b) ((a)>(b)?(a):(b))
#endif
