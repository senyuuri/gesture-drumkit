/*
 * Copyright (c) 2015 Samsung Electronics Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __HELLO_ACCESSORY_CONSUMER_H__
#define __HELLO_ACCESSORY_CONSUMER_H__

#include <app.h>
#include <glib.h>
#include <Elementary.h>
#include <system_settings.h>
#include <efl_extension.h>
#include <dlog.h>

#define TAG "HelloAccessoryConsumer"

void     initialize_sap();
gboolean find_peers();
gboolean request_service_connection(void);
gboolean terminate_service_connection(void);
gboolean send_data(int message_len, void *message);
void     update_ui(char *data);

#if !defined(PACKAGE)
#define PACKAGE "org.tizen.helloaccessoryconsumer"
#endif

#endif //__HELLO_ACCESSORY_CONSUMER_H__
