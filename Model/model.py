"""
usage: python model.py
"""

import os
import time
import random
import pandas as pd
import numpy as np
import pickle
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split
from sklearn.metrics import confusion_matrix
from keras.models import Sequential
from keras.layers import Dense, BatchNormalization
from keras.optimizers import Adam
from keras.models import load_model
from keras.callbacks import ModelCheckpoint


# set BASE_DATA_DIR to the right path
BASE_DATA_DIR = '/Users/peixuan/PycharmProjects/gesture-drumkit/Model/'
GESTURES_DIRS = ['gesture-up', 'gesture-down', 'gesture-none']

PARSED_DATA_DIR = os.path.join(BASE_DATA_DIR, 'data/parsed')
MODELS_DIR = os.path.join(BASE_DATA_DIR, 'models')

DATASET_FILE = 'dataset.pk'
BEST_MODEL_FILE = 'best_model.pkl'
BEST_MODEL_FILEPATH = os.path.join(MODELS_DIR, BEST_MODEL_FILE)


def balance_dataset(data):
    """
    balances dataset by up-sampling classes with less datums
    :param data: list of list of (data, label) by classes
    """
    print('balancing dataset...')
    max_len = max([len(x) for x in data])
    balanced_data = []
    for cls_data in data:
        if len(cls_data) < max_len:
            generated = random.sample(cls_data, max_len - len(cls_data))
            cls_data = cls_data + generated
        balanced_data.append(cls_data)
    return balanced_data


def load_dataset(do_balance=True, do_shuffle=True, save_dataset=True):
    """
    loads gestures data and prepares it for the model
    :param do_balance: up-sample minority classes to majority class
    :param do_shuffle: shuffle the dataset
    :param save_dataset: writes loadable dataset into 'dataset.pk'
    :return: processed dataset in the form (data_values, data_labels)
    """

    # load all gestures data
    print('loading dataset...')
    data = []
    for i, gesture in enumerate(GESTURES_DIRS):
        print('loading data for gesture: {}...'.format(gesture))
        cls_data = []
        dir_path = os.path.join(PARSED_DATA_DIR, gesture)
        assert os.path.isdir(dir_path), 'invalid gestures data directory'
        for fname in os.listdir(dir_path):
            df = pd.read_csv(os.path.join(dir_path, '') + fname, header=None)
            header = ['hid', 'htype', 'htime', 'hx', 'hy', 'hz', 'hrms']
            gethidx = lambda x: header.index(x)
            drop_cols = df.columns[[gethidx('hid'), gethidx('htype'), gethidx('hrms'), gethidx('htime')]]
            df.drop(drop_cols, axis=1, inplace=True)
            cls_data.append((df.values.flatten(), i))
        print('{} 1x{} datums loaded'.format(len(cls_data), len(cls_data[0][0])))
        data.append(cls_data)

    # balance the dataset
    if do_balance:
        data = balance_dataset(data)

    # shuffle the dataset
    if do_shuffle:
        data = random.sample(data, len(data))

    # split data into (values, labels)
    vals = []
    labels = []
    for cls_data in data:
        for cls_datum in cls_data:
            datum_val, datum_label = cls_datum
            vals.append(datum_val)
            labels.append([1 if datum_label == i else 0 for i in range(len(GESTURES_DIRS))])

    if save_dataset:
        print('saving dataset to file...')
        with open(DATASET_FILE, 'wb') as pk:
            pickle.dump(vals, pk)
            pickle.dump(labels, pk)

    # return vals, labels


def train_model(vals, labels, test_size=0.2, do_eval=True):
    """
    trains a simple MLP
    """
    train_x, test_x, train_y, test_y = [np.array(x) for x in train_test_split(vals, labels, test_size=test_size)]
    input_shape = train_x[0].shape

    # build the model
    model = Sequential()
    model.add(Dense(128, activation='relu', input_shape=input_shape))
    model.add(BatchNormalization())
    model.add(Dense(128, activation='relu'))
    model.add(Dense(len(GESTURES_DIRS), activation='softmax'))
    model.summary()

    # run the model
    loss = 'categorical_crossentropy'
    optim = Adam(lr=0.0001)
    checkpt = ModelCheckpoint(BEST_MODEL_FILEPATH, monitor='val_acc', save_best_only=True, mode='max', verbose=1)
    model.compile(loss=loss, optimizer=optim, metrics=['accuracy'])
    model.fit(train_x, train_y, epochs=4, batch_size=8, callbacks=[checkpt], validation_split=0.1)

    # evaluate the model
    if do_eval:
        model = load_model(BEST_MODEL_FILEPATH)
        predictions = model.predict(test_x)
        num_correct = 0
        pred_y = []
        true_y = []
        for i, pred in enumerate(predictions):
            pred_cls = np.argmax(pred)
            true_cls = np.argmax(test_y[i])
            pred_y.append(pred_cls)
            true_y.append(true_cls)
            num_correct += 1 if pred_cls == true_cls else 0
        print('accuracy: {0:.2f}'.format(num_correct / len(predictions)))
        print('confusion matrix:')
        print(confusion_matrix(true_y, pred_y))


def plot_confusion_matrix(y_true, y_pred, classes, normalize=True, title=None, cmap=plt.cm.Blues):
    """
    This function prints and plots the confusion matrix.
    Normalization can be applied by setting `normalize=True`.
    """
    # from https://scikit-learn.org/stable/auto_examples/model_selection/plot_confusion_matrix.html
    if not title:
        if normalize:
            title = 'Normalized confusion matrix'
        else:
            title = 'Confusion matrix, without normalization'

    # Compute confusion matrix
    cm = confusion_matrix(y_true, y_pred)
    if normalize:
        cm = cm.astype('float') / cm.sum(axis=1)[:, np.newaxis]
        print("Normalized confusion matrix")
    else:
        print('Confusion matrix, without normalization')

    print(cm)

    fig, ax = plt.subplots()
    im = ax.imshow(cm, interpolation='nearest', cmap=cmap)
    ax.figure.colorbar(im, ax=ax)
    # We want to show all ticks...
    ax.set(xticks=np.arange(cm.shape[1]),
           yticks=np.arange(cm.shape[0]),
           # ... and label them with the respective list entries
           xticklabels=classes, yticklabels=classes,
           title=title,
           ylabel='True label',
           xlabel='Predicted label')

    # Rotate the tick labels and set their alignment.
    plt.setp(ax.get_xticklabels(), rotation=45, ha="right",
             rotation_mode="anchor")

    # Loop over data dimensions and create text annotations.
    fmt = '.2f' if normalize else 'd'
    thresh = cm.max() / 2.
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            ax.text(j, i, format(cm[i, j], fmt),
                    ha="center", va="center",
                    color="white" if cm[i, j] > thresh else "black")
    fig.tight_layout()
    return ax


def main():
    np.random.seed(0)
    random.seed(0)

    start_time = time.time()

    # only have to call load_dataset once, saves time for dev model tuning purposes
    # load_dataset()

    # after calling load_dataset, read saved data directly from file
    with open(DATASET_FILE, 'rb') as pk:
        vals = pickle.load(pk)
        labels = pickle.load(pk)
    train_model(vals, labels)

    end_time = time.time()
    print('time taken: {0:.2f} seconds'.format(end_time - start_time))


if __name__ == '__main__':
    main()
