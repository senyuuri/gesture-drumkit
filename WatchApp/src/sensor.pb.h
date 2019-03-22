/* Automatically generated nanopb header */
/* Generated by nanopb-0.3.9.3 at Fri Mar 22 11:47:56 2019. */

#ifndef PB_SENSOR_PB_H_INCLUDED
#define PB_SENSOR_PB_H_INCLUDED
#include "pb.h"

/* @@protoc_insertion_point(includes) */
#if PB_PROTO_HEADER_VERSION != 30
#error Regenerate this file with the current version of nanopb generator.
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* Enum definitions */
typedef enum _WatchPacket_SensorMessage_SensorType {
    WatchPacket_SensorMessage_SensorType_ACCELEROMETER = 0,
    WatchPacket_SensorMessage_SensorType_GYROSCOPE = 1
} WatchPacket_SensorMessage_SensorType;
#define _WatchPacket_SensorMessage_SensorType_MIN WatchPacket_SensorMessage_SensorType_ACCELEROMETER
#define _WatchPacket_SensorMessage_SensorType_MAX WatchPacket_SensorMessage_SensorType_GYROSCOPE
#define _WatchPacket_SensorMessage_SensorType_ARRAYSIZE ((WatchPacket_SensorMessage_SensorType)(WatchPacket_SensorMessage_SensorType_GYROSCOPE+1))

/* Struct definitions */
typedef struct _WatchPacket_SensorMessage {
    WatchPacket_SensorMessage_SensorType sensor_type;
    pb_size_t data_count;
    float data[3];
    uint64_t timestamp;
/* @@protoc_insertion_point(struct:WatchPacket_SensorMessage) */
} WatchPacket_SensorMessage;

typedef struct _WatchPacket {
    pb_size_t messages_count;
    WatchPacket_SensorMessage messages[10];
/* @@protoc_insertion_point(struct:WatchPacket) */
} WatchPacket;

/* Default values for struct fields */

/* Initializer values for message structs */
#define WatchPacket_init_default                 {0, {WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default, WatchPacket_SensorMessage_init_default}}
#define WatchPacket_SensorMessage_init_default   {_WatchPacket_SensorMessage_SensorType_MIN, 0, {0, 0, 0}, 0}
#define WatchPacket_init_zero                    {0, {WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero, WatchPacket_SensorMessage_init_zero}}
#define WatchPacket_SensorMessage_init_zero      {_WatchPacket_SensorMessage_SensorType_MIN, 0, {0, 0, 0}, 0}

/* Field tags (for use in manual encoding/decoding) */
#define WatchPacket_SensorMessage_sensor_type_tag 1
#define WatchPacket_SensorMessage_data_tag       2
#define WatchPacket_SensorMessage_timestamp_tag  3
#define WatchPacket_messages_tag                 1

/* Struct field encoding specification for nanopb */
extern const pb_field_t WatchPacket_fields[2];
extern const pb_field_t WatchPacket_SensorMessage_fields[4];

/* Maximum encoded size of messages (where known) */
#define WatchPacket_size                         300
#define WatchPacket_SensorMessage_size           28

/* Message IDs (where set with "msgid" option) */
#ifdef PB_MSGID

#define SENSOR_MESSAGES \


#endif

#ifdef __cplusplus
} /* extern "C" */
#endif
/* @@protoc_insertion_point(eof) */

#endif
