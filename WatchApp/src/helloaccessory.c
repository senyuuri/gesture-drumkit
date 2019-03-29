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

#include "helloaccessory.h"
#include "pb_encode.h"
#include "sensor.pb.h"
#include <sensor.h>

// to get 200hz, request an update every 5 ms
#define UPDATE_INTERVAL 5
#define SENSOR_COUNT 2
// (9x2) messages uses about 430 bytes
#define BUFFER_SIZE 600
// change the value in sensor.options if touching this (9*2)
#define MESSAGES_COUNT 10*SENSOR_COUNT

static sensor_type_e sensors_used[] = { SENSOR_ACCELEROMETER, SENSOR_GYROSCOPE };
static bool started_sensors = false;

void _data_finalize(void);
static void _initialize_sensors(void);
static void _send_message(void);

typedef struct _sensor_data {
	sensor_h handle;
	sensor_listener_h listener;
} sensor_data_t;

static struct data_info {
	sensor_data_t sensors[SENSOR_COUNT];
	uint8_t buffer[BUFFER_SIZE];
	WatchPacket_SensorMessage message_cache[MESSAGES_COUNT];
	uint8_t cache_idx;
	size_t message_len;
} s_info = {
	.sensors = { {0}, },
	.message_cache = { {0}, },
	.cache_idx = 0
};

typedef struct appdata {
	Evas_Object *win;
	Evas_Object *naviframe;
	Evas_Object *rect[10];
	Eext_Circle_Surface *circle_surface;
	Evas_Object *circle_genlist;
} appdata_s;

static appdata_s *object;
static void win_delete_request_cb(void *data, Evas_Object *obj, void *event_info)
{
	ui_app_exit();
}

static void _timeout_cb(void *data, Evas_Object *obj, void *event_info)
{
	if (!obj) return;
	elm_popup_dismiss(obj);
}

static void _block_clicked_cb(void *data, Evas_Object *obj, void *event_info)
{
	if (!obj) return;
	elm_popup_dismiss(obj);
}

static void _popup_hide_cb(void *data, Evas_Object *obj, void *event_info)
{
	if (!obj) return;
	elm_popup_dismiss(obj);
}

static void _popup_hide_finished_cb(void *data, Evas_Object *obj, void *event_info)
{
	if (!obj) return;
	evas_object_del(obj);
}

static void _popup_toast_cb(Evas_Object *parent, char *string)
{
	Evas_Object *popup;

	popup = elm_popup_add(parent);
	elm_object_style_set(popup, "toast/circle");
	elm_popup_orient_set(popup, ELM_POPUP_ORIENT_BOTTOM);
	evas_object_size_hint_weight_set(popup, EVAS_HINT_EXPAND, EVAS_HINT_EXPAND);
	eext_object_event_callback_add(popup, EEXT_CALLBACK_BACK, _popup_hide_cb, NULL);
	evas_object_smart_callback_add(popup, "dismissed", _popup_hide_finished_cb, NULL);
	elm_object_part_text_set(popup, "elm.text", string);

	evas_object_smart_callback_add(popup, "block,clicked", _block_clicked_cb, NULL);

	elm_popup_timeout_set(popup, 2.0);
	evas_object_smart_callback_add(popup, "timeout", _timeout_cb, NULL);

	evas_object_show(popup);
}

void update_ui(char *data)
{
	dlog_print(DLOG_INFO, TAG, "Updating UI with data %s", data);
	_popup_toast_cb(object->naviframe, data);
}


static void btn_cb_connect(void *data, Evas_Object *obj, void *event_info)
{
	Elm_Object_Item *it = event_info;
	elm_genlist_item_selected_set(it, EINA_FALSE);

	dlog_print(DLOG_DEBUG, TAG, "AGENT_INITIALISED");
	find_peers();

}

static void btn_cb_send(void *data, Evas_Object *obj, void *event_info)
{
	if (!started_sensors) {
		Elm_Object_Item *it = event_info;
		elm_genlist_item_selected_set(it, EINA_FALSE);

		dlog_print(DLOG_DEBUG, TAG, "initializing sensors");
		_initialize_sensors();
		started_sensors = true;
	} else {
		dlog_print(DLOG_INFO, TAG, "already started sensors");
	}

}

static void btn_cb_disconnect(void *data, Evas_Object *obj, void *event_info)
{
	Elm_Object_Item *it = event_info;
	elm_genlist_item_selected_set(it, EINA_FALSE);

	_data_finalize();
	terminate_service_connection();
}

char *main_menu_names[] = {
	"Connect", "Send Sensor Data", "Disconnect",
	NULL
};

typedef struct _item_data {
	int index;
	Elm_Object_Item *item;
} item_data;

static char *_gl_title_text_get(void *data, Evas_Object *obj, const char *part)
{
	char buf[1024];

	snprintf(buf, 1023, "%s", "HelloAccessory");

	return strdup(buf);
}

static char *_gl_sub_title_text_get(void *data, Evas_Object *obj, const char *part)
{
	char buf[1024];

	snprintf(buf, 1023, "%s", "Consumer");

	return strdup(buf);
}

static char *_gl_main_text_get(void *data, Evas_Object *obj, const char *part)
{
	char buf[1024];
	item_data *id = data;
	int index = id->index;

	if (!strcmp(part, "elm.text"))
		snprintf(buf, 1023, "%s", main_menu_names[index - 1]);

	return strdup(buf);
}


static void _gl_del(void *data, Evas_Object *obj)
{
	// FIXME: Unrealized callback can be called after this.
	// Accessing Item_Data can be dangerous on unrealized callback.
	item_data *id = data;
	if (id) free(id);
}

static Eina_Bool _naviframe_pop_cb(void *data, Elm_Object_Item *it)
{
	ui_app_exit();
	return EINA_FALSE;
}

static void create_list_view(appdata_s *ad)
{
	Evas_Object *genlist = NULL;
	Evas_Object *naviframe = ad->naviframe;
	Elm_Object_Item *nf_it = NULL;
	item_data *id = NULL;
	int index = 0;

	Elm_Genlist_Item_Class *itc = elm_genlist_item_class_new();
	Elm_Genlist_Item_Class *titc = elm_genlist_item_class_new();
	Elm_Genlist_Item_Class *pitc = elm_genlist_item_class_new();
	Elm_Genlist_Item_Class *gic = elm_genlist_item_class_new();

	/* Genlist Item Style */
	itc->item_style = "1text";
	itc->func.text_get = _gl_main_text_get;
	itc->func.del = _gl_del;

	/* Genlist Title Item Style */
	titc->item_style = "title";
	titc->func.text_get = _gl_title_text_get;
	titc->func.del = _gl_del;

	gic->item_style = "groupindex";
	gic->func.text_get = _gl_sub_title_text_get;
	gic->func.del = _gl_del;

	pitc->item_style = "padding";

	/* Create Genlist */
	genlist = elm_genlist_add(naviframe);
	elm_genlist_mode_set(genlist, ELM_LIST_COMPRESS);
	evas_object_smart_callback_add(genlist, "selected", NULL, NULL);

	/* Create Circle Genlist */
	ad->circle_genlist = eext_circle_object_genlist_add(genlist, ad->circle_surface);

	/* Set Scroller Policy */
	eext_circle_object_genlist_scroller_policy_set(ad->circle_genlist, ELM_SCROLLER_POLICY_OFF, ELM_SCROLLER_POLICY_AUTO);

	/* Activate Rotary Event */
	eext_rotary_object_event_activated_set(ad->circle_genlist, EINA_TRUE);

	/* Title Item Here */
	id = calloc(sizeof(item_data), 1);
	elm_genlist_item_append(genlist, titc, NULL, NULL, ELM_GENLIST_ITEM_GROUP, NULL, NULL);

	id = calloc(sizeof(item_data), 1);
	id->index = index++;
	id->item = elm_genlist_item_append(genlist, gic, id, NULL, ELM_GENLIST_ITEM_GROUP, NULL, NULL);

	/* Main Menu Items Here*/
	id = calloc(sizeof(item_data), 1);
	id->index = index++;
	id->item = elm_genlist_item_append(genlist, itc, id, NULL, ELM_GENLIST_ITEM_NONE, btn_cb_connect, ad);
	id = calloc(sizeof(item_data), 1);
	id->index = index++;
	id->item = elm_genlist_item_append(genlist, itc, id, NULL, ELM_GENLIST_ITEM_NONE, btn_cb_send, ad);
	id = calloc(sizeof(item_data), 1);
	id->index = index++;
	id->item = elm_genlist_item_append(genlist, itc, id, NULL, ELM_GENLIST_ITEM_NONE, btn_cb_disconnect, ad);

	/* Padding Item Here */
	elm_genlist_item_append(genlist, pitc, NULL, NULL, ELM_GENLIST_ITEM_NONE, NULL, ad);

	nf_it = elm_naviframe_item_push(naviframe, NULL, NULL, NULL, genlist, "empty");
	elm_naviframe_item_pop_cb_set(nf_it, _naviframe_pop_cb, ad->win);


}

static void create_base_gui(appdata_s *ad)
{
	Evas_Object *conform = NULL;

	/* Window */
	ad->win = elm_win_util_standard_add(PACKAGE, PACKAGE);
	elm_win_autodel_set(ad->win, EINA_TRUE);

	evas_object_smart_callback_add(ad->win, "delete,request", win_delete_request_cb, NULL);

	/* Conformant */
	conform = elm_conformant_add(ad->win);
	evas_object_size_hint_weight_set(conform, EVAS_HINT_EXPAND, EVAS_HINT_EXPAND);
	elm_win_resize_object_add(ad->win, conform);
	evas_object_show(conform);

	/* Naviframe */
	ad->naviframe = elm_naviframe_add(conform);
	elm_object_content_set(conform, ad->naviframe);

	/* Eext Circle Surface*/
	ad->circle_surface = eext_circle_surface_naviframe_add(ad->naviframe);

	/* Main View */
	create_list_view(ad);

	eext_object_event_callback_add(ad->naviframe, EEXT_CALLBACK_BACK, eext_naviframe_back_cb, NULL);
	eext_object_event_callback_add(ad->naviframe, EEXT_CALLBACK_MORE, eext_naviframe_more_cb, NULL);

	/* Show window after base gui is set up */
	evas_object_show(ad->win);
}

static bool app_create(void *data)
{
	/* Hook to take necessary actions before main event loop starts
	   Initialize UI resources and application's data
	   If this function returns true, the main loop of application starts
	   If this function returns false, the application is terminated */
	object = data;

	create_base_gui(object);
	initialize_sap();

	return TRUE;
}

static void app_control(app_control_h app_control, void *data)
{
	/* Handle the launch request. */
}

static void app_pause(void *data)
{
	/* Take necessary actions when application becomes invisible. */
}

static void app_resume(void *data)
{
	/* Take necessary actions when application becomes visible. */
}

static void app_terminate(void *data)
{
	/* Release all resources. */
}

static void ui_app_lang_changed(app_event_info_h event_info, void *user_data)
{
	/*APP_EVENT_LANGUAGE_CHANGED*/
	char *locale = NULL;
	system_settings_get_value_string(SYSTEM_SETTINGS_KEY_LOCALE_LANGUAGE, &locale);
	elm_language_set(locale);
	free(locale);
	return;
}

static void ui_app_orient_changed(app_event_info_h event_info, void *user_data)
{
	/*APP_EVENT_DEVICE_ORIENTATION_CHANGED*/
	return;
}

static void ui_app_region_changed(app_event_info_h event_info, void *user_data)
{
	/*APP_EVENT_REGION_FORMAT_CHANGED*/
}

static void ui_app_low_battery(app_event_info_h event_info, void *user_data)
{
	/*APP_EVENT_LOW_BATTERY*/
}

static void ui_app_low_memory(app_event_info_h event_info, void *user_data)
{
	/*APP_EVENT_LOW_MEMORY*/
}

/**
 *  Get timestamp in ms from event time
 *  https://developer.tizen.org/ko/forums/native-application-development/sensor-event-timestamp
 */
static unsigned long long clock_real_time_from_event_time(uint64_t event_time)
{
	struct timespec spec;
	clock_gettime(CLOCK_REALTIME, &spec);
	unsigned long long current_time_ms = spec.tv_sec * 1000LL + spec.tv_nsec / 1000000LL;
	clock_gettime(CLOCK_MONOTONIC, &spec);
	unsigned long long monotonic_time_ms = spec.tv_sec * 1000LL + spec.tv_nsec / 1000000LL;
	unsigned long long event_time_ms = current_time_ms - monotonic_time_ms + event_time / 1000LL;
	return event_time_ms;
}

/**
 * @brief Callback invoked by a sensor's listener.
 * @param sensor The sensor's handle.
 * @param event The event data.
 * @param data The user data.
 */
static void _sensor_event_cb(sensor_h sensor, sensor_event_s *event, void *data)
{
	int sensor_idx = (int) data;
	unsigned long long event_time_ms = clock_real_time_from_event_time(event->timestamp);

	WatchPacket_SensorMessage* msg = &s_info.message_cache[s_info.cache_idx];
	msg->sensor_type = sensor_idx;
	// msg->data_count = event->value_count;
	msg->timestamp = event_time_ms;

	float *values = &event->values[0];
	for (int i = 0; i < event->value_count; i++) {
		msg->data[i] = values[i];
	}

	s_info.cache_idx++;

	if (s_info.cache_idx == MESSAGES_COUNT) {
		_send_message();
	}
}


static void _threaded_send_msg(void *data, Ecore_Thread *thread)
{	
	WatchPacket *packet = (WatchPacket*) data;
	pb_ostream_t stream = pb_ostream_from_buffer(s_info.buffer, BUFFER_SIZE);
	bool status = pb_encode(&stream, WatchPacket_fields, packet);
	size_t message_len = stream.bytes_written;

	if (!status)
	{
		dlog_print(DLOG_DEBUG, TAG, "Encoding msg failed");
		dlog_print(DLOG_DEBUG, TAG, PB_GET_ERROR(&stream));
	} 
	else 
	{
		send_data(message_len, s_info.buffer);
	}
	free(packet);
}


static void _send_message() {
	WatchPacket *packet = calloc(1, WatchPacket_size);
	//packet->messages_count = MESSAGES_COUNT;

	for (int i = 0; i < MESSAGES_COUNT; i++) {
		// struct copy
		packet->messages[i] = s_info.message_cache[i];
	}

	// reset cache_idx after copying all the messages
	s_info.cache_idx = 0;

	ecore_thread_run(_threaded_send_msg, NULL, NULL, packet);
}

/**
 * @brief Function used to destroy the sensor listeners. Should be invoked when the app is terminated.
 */
void _data_finalize(void)
{
	int ret = SENSOR_ERROR_NONE;
	int i;

	for (i = 0; i < SENSOR_COUNT; ++i) {
		ret = sensor_destroy_listener(s_info.sensors[i].listener);
		if (ret != SENSOR_ERROR_NONE) {
			dlog_print(DLOG_ERROR, LOG_TAG, "[%s:%d] sensor_get_default_sensor() error: %s", __FILE__, __LINE__, get_error_message(ret));
			continue;
		}
	}
	started_sensors = false;
}

static void _initialize_sensors(void)
{
	dlog_print(DLOG_DEBUG, TAG, "init sensors");

	int ret;
	for (int i = 0; i < SENSOR_COUNT; i++) {
		sensor_type_e st = sensors_used[i];

		ret = sensor_get_default_sensor(st, &s_info.sensors[i].handle);
		if (ret != SENSOR_ERROR_NONE) {
			dlog_print(DLOG_ERROR, TAG, "[%s:%d] sensor_get_default_sensor() error: %s", __FILE__, __LINE__, get_error_message(ret));
			continue;
		}

		ret = sensor_create_listener(s_info.sensors[i].handle, &s_info.sensors[i].listener);
		if (ret != SENSOR_ERROR_NONE) {
			dlog_print(DLOG_ERROR, TAG, "[%s:%d] sensor_create_listener() error: %s", __FILE__, __LINE__, get_error_message(ret));
			continue;
		}

		ret = sensor_listener_set_event_cb(s_info.sensors[i].listener, UPDATE_INTERVAL, _sensor_event_cb, (void*)i);
		if (ret != SENSOR_ERROR_NONE) {
			dlog_print(DLOG_ERROR, TAG, "[%s:%d] sensor_listener_set_event_cb() error: %s", __FILE__, __LINE__, get_error_message(ret));
			continue;
		}

		ret = sensor_listener_set_option(s_info.sensors[i].listener, SENSOR_OPTION_ALWAYS_ON);
		if (ret != SENSOR_ERROR_NONE) {
			dlog_print(DLOG_ERROR, TAG, "[%s:%d] sensor_listener_set_option() error: %s", __FILE__, __LINE__, get_error_message(ret));
			continue;
		}

		ret = sensor_listener_start(s_info.sensors[i].listener);
		if (ret != SENSOR_ERROR_NONE)
		{
			dlog_print(DLOG_ERROR, LOG_TAG, "[%s:%d] sensor_listener_start() error: %s", __FILE__, __LINE__, get_error_message(ret));
			return;
		}

		dlog_print(DLOG_DEBUG, TAG, "started sensor type: %d", st);
	}
}

void setup_ecore() {
	ecore_thread_max_set(1);
}


int main(int argc, char *argv[])
{
	appdata_s ad = { 0, };
	int ret = 0;

	ui_app_lifecycle_callback_s event_callback = { 0, };
	app_event_handler_h handlers[5] = { NULL, };

	event_callback.create = app_create;
	event_callback.terminate = app_terminate;
	event_callback.pause = app_pause;
	event_callback.resume = app_resume;
	event_callback.app_control = app_control;
	setup_ecore();

	ui_app_add_event_handler(&handlers[APP_EVENT_LOW_BATTERY], APP_EVENT_LOW_BATTERY, ui_app_low_battery, &ad);
	ui_app_add_event_handler(&handlers[APP_EVENT_LOW_MEMORY], APP_EVENT_LOW_MEMORY, ui_app_low_memory, &ad);
	ui_app_add_event_handler(&handlers[APP_EVENT_DEVICE_ORIENTATION_CHANGED], APP_EVENT_DEVICE_ORIENTATION_CHANGED, ui_app_orient_changed, &ad);
	ui_app_add_event_handler(&handlers[APP_EVENT_LANGUAGE_CHANGED], APP_EVENT_LANGUAGE_CHANGED, ui_app_lang_changed, &ad);
	ui_app_add_event_handler(&handlers[APP_EVENT_REGION_FORMAT_CHANGED], APP_EVENT_REGION_FORMAT_CHANGED, ui_app_region_changed, &ad);
	ui_app_remove_event_handler(handlers[APP_EVENT_LOW_MEMORY]);

	ret = ui_app_main(argc, argv, &event_callback, &ad);
	if (ret != APP_ERROR_NONE) {
		dlog_print(DLOG_ERROR, TAG, "ui_app_main() is failed. err = %d", ret);
	}

	return ret;
}
