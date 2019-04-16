"""
usage: python data.py
"""

import pandas as pd
import matplotlib.pyplot as plt
import os
import math
import shutil
from scipy import signal
import random


# set BASE_DATA_DIR to the right path
BASE_DATA_DIR = '/Users/peixuan/PycharmProjects/gesture-drumkit/Model/data'
RAW_DATA_DIR = os.path.join(BASE_DATA_DIR, 'raw')
PARSED_DATA_DIR = os.path.join(BASE_DATA_DIR, 'parsed')


# define some set variables
# num of frames in a window. 50 * 5ms = 250ms
WINDOW_SIZE = 50
FRAME_BEFORE_PEAK = 35
# for each peak detected, we treat adjacent +-PEAK_DELTA frames also as peak
# as long as a window's center fall in the PEAK_DELTA's range, the window is considered to have gesture onset
PEAK_DELTA = 10
FS_GESTURE_NONE = 0.1


def draw(acce_df, gyro_df, acce_peaks, gyro_peaks, raw_file):
    """
    visualise overall peaks + slices and zoom in to show the first 2000 samples
    """
    SIZE = 2000
    fig, axes = plt.subplots(nrows=2, ncols=2)
    fig.suptitle(raw_file)

    # draw rms + peaks
    axes[0, 0].set_title('acce_df')
    axes[0, 0].plot(acce_df[HTIME], acce_df[HRMS])
    axes[0, 0].plot(acce_df[HTIME][acce_peaks], acce_df[HRMS][acce_peaks], 'x')
    axes[1, 0].set_title('gyro_df')
    axes[1, 0].plot(gyro_df[HTIME], gyro_df[HRMS])
    axes[1, 0].plot(gyro_df[HTIME][gyro_peaks], gyro_df[HRMS][gyro_peaks], 'x')

    # draw first SIZE samples
    acce_df_tmp = acce_df[[HX, HY, HZ, HRMS, HTIME]][:SIZE]
    acce_df_tmp.plot(x=HTIME, ax=axes[0, 1])
    gyro_df_tmp = gyro_df[[HX, HY, HZ, HRMS, HTIME]][:SIZE]
    gyro_df_tmp.plot(x=HTIME, ax=axes[1, 1])
    acce_mean_peak = acce_df[HRMS].mean()
    acce_peaks_begin, _ = signal.find_peaks(acce_df[HRMS][:SIZE], distance=100, height=acce_mean_peak)
    axes[0, 1].plot(acce_df[HTIME][acce_peaks_begin], acce_df[HRMS][acce_peaks_begin], 'x')

    gyro_mean_peak = gyro_df[HRMS].mean()
    gyro_peaks_begin, _ = signal.find_peaks(gyro_df[HRMS][:SIZE], distance=100, height=gyro_mean_peak)
    axes[1, 1].plot(gyro_df[HTIME][gyro_peaks_begin], gyro_df[HRMS][gyro_peaks_begin], 'x')

    for p in acce_peaks_begin:
        # ignore incomplete window
        start = p - FRAME_BEFORE_PEAK
        end = start + WINDOW_SIZE
        if start > 0 and end < len(acce_df):
            start_time = acce_df[HTIME][start]
            end_time = acce_df[HTIME][end]

            # draw selected slices
            axes[0, 1].axvspan(start_time, end_time, alpha=0.1, color='red')
            axes[1, 1].axvspan(start_time, end_time, alpha=0.1, color='red')


def expand_peak_range(peaks, df_size):
    """
    given an array of peaks, for each peak index p, expand it to (p - PEAK_DELTA, p + PEAK_DELTA)
    :param peaks - a numpy 1d array of peak indexes in a dataframe
    :param df_size - size of the dataframe
    """
    peak_range = []
    for _, p in enumerate(peaks):
        p_left = p - PEAK_DELTA
        p_right = p + PEAK_DELTA
        if p_left >= 0 and p_right < df_size:
            peak_range.append((p_left, p_right))
    return peak_range


def is_index_in_peak_range(idx, peak_range):
    """
    check if an index falls into any of the peak range
    :param idx: the index of a window's center
    :param peak_range: a list of tuples, each tuple represents a range of peak(inclusive)
    :return: boolean
    """
    for p_left, p_right in peak_range:
        if p_left <= idx <= p_right:
            return True
    return False


def write_slice_to_file(output_dir, acce_df, gyro_df, peak, bpm_tag, f_idx):
    """
    derive the window position from the peak index, truncate both sensors' data and write to a given file at fpath
    :param bpm_tag: string of 'bpm%d'
    :param f_idx: output file index
    """
    # ignore incomplete window
    start = peak - FRAME_BEFORE_PEAK
    end = start + WINDOW_SIZE
    if start > 0 and end < len(acce_df):
        start_time = acce_df[HTIME][start]
        end_time = acce_df[HTIME][end]
        fout_name = '%s_%d_%d_%d.csv' % (bpm_tag, f_idx, start_time, end_time)
        gyro_range = gyro_df[HTIME].searchsorted([start_time, end_time])

        with open(os.path.join(output_dir, fout_name), 'a') as f:
            trunc_acce = acce_df.truncate(before=start, after=end - 1)
            trunc_acce.to_csv(f, header=False)
            trunc_gyro = gyro_df.truncate(before=gyro_range[0], after=gyro_range[1])

            # normalise gyroscope sample size to be exactly the same as WINDOW_SIZE
            if trunc_gyro.shape[0] < WINDOW_SIZE:
                for i in range(trunc_gyro.shape[0], WINDOW_SIZE):
                    trunc_gyro.loc[i] = ['GYROSCOPE', 0, 0, 0, 0, 0]
            trunc_gyro = trunc_gyro[:WINDOW_SIZE]
            trunc_gyro.to_csv(f, header=False)
        return trunc_acce.shape[0] + trunc_gyro.shape[0]
    return 0


def batch_data_from_dir(do_draw=False):

    # uniq id for gesture-none file
    fnone_uid = 0

    raw_files = os.listdir(RAW_DATA_DIR)

    # for each .csv raw file
    for idx, raw_file in enumerate(raw_files):
        print('processing {}...'.format(raw_file))

        # read file
        df = pd.read_csv(os.path.join(RAW_DATA_DIR, raw_file))
        header = df.columns.values.tolist()
        global HTYPE, HTIME, HX, HY, HZ
        HTYPE, HTIME, HX, HY, HZ = header

        # create the output folder if not exists
        gesture_name = ('-'.join(raw_file.split('-')[:2]))
        gesture_dir = os.path.join(PARSED_DATA_DIR, gesture_name)
        if not os.path.exists(gesture_dir):
            os.mkdir(gesture_dir)

        # get bpm info
        bpm_tag = raw_file.split('-')[2]

        # normalise timestamp in ms
        min_time = df[HTIME].min()
        df[HTIME] = df[HTIME].values - min_time

        # add rms as a feature
        global HRMS
        HRMS = 'rms'
        df[HRMS] = df.apply(lambda row: math.sqrt(row[HX] ** 2 + row[HY] ** 2 + row[HZ] ** 2), axis=1)

        # get gyroscope and accelerometer data
        df_gyro = df.loc[df[HTYPE] == 'GYROSCOPE'].reset_index(drop=True)
        df_accel = df.loc[df[HTYPE] == 'ACCELEROMETER'].reset_index(drop=True)

        # get gyro and accel peaks
        df_gyro_mean_peak = df_gyro[HRMS].mean()
        df_gyro_peaks, _ = signal.find_peaks(df_gyro[HRMS], distance=100, height=df_gyro_mean_peak)
        df_accel_mean_peak = df_accel[HRMS].mean()
        df_accel_peaks, _ = signal.find_peaks(df_accel[HRMS], distance=100, height=df_accel_mean_peak)

        # get accel peak range
        df_accel_peak_range = expand_peak_range(df_accel_peaks, df_accel.shape[0])

        # visualise peaks
        if do_draw:
            draw(df_accel, df_gyro, df_accel_peaks, df_gyro_peaks, raw_file)

        # extract frame surrounding the peak and save as a datapoint
        f_uid = 0
        count_gesture_none = 0
        count_selected_gesture_none = 0
        for i in range(df_accel.shape[0]):
            if is_index_in_peak_range(i, df_accel_peak_range):
                write_slice_to_file(gesture_dir, df_accel, df_gyro, i, bpm_tag, f_uid)
                f_uid += 1
            else:
                count_gesture_none += 1
                if random.random() < FS_GESTURE_NONE:
                    gesture_file = os.path.join(PARSED_DATA_DIR, 'gesture-none')
                    write_slice_to_file(gesture_file, df_accel, df_gyro, i, 'bpm0', fnone_uid)
                    fnone_uid += 1
                    count_selected_gesture_none += 1
            if i % 1000 == 0:
                print('  progress %d/%d' % (i, df_accel.shape[0]))

        print('(%d/%d) done. %d gestures + %d/%d non-gestures.' % (
            idx + 1, len(raw_files), f_uid, count_selected_gesture_none, count_gesture_none))


def main():
    assert os.path.isdir(BASE_DATA_DIR), 'base data directory is invalid'
    assert os.path.isdir(RAW_DATA_DIR), 'raw data directory is invalid'
    assert len(os.listdir(RAW_DATA_DIR)) > 0, 'raw data directory is empty'

    # set up output dir
    if os.path.isdir(PARSED_DATA_DIR):
        shutil.rmtree(PARSED_DATA_DIR)
    os.mkdir(PARSED_DATA_DIR)
    os.mkdir(os.path.join(PARSED_DATA_DIR, 'gesture-none'))

    print('sampling rate for non-gesture: {}'.format(FS_GESTURE_NONE))

    print('batching data from dir...')
    batch_data_from_dir()


if __name__ == '__main__':
    main()
