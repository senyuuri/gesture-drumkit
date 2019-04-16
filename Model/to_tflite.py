"""
usage: python to_tflite.py
"""

import tensorflow as tf
import keras
import os

# set BASE_MODES_DIR to the right path
BASE_MODELS_DIR = '/Users/peixuan/PycharmProjects/gesture-drumkit/Model/models'

best_model_file = os.path.join(BASE_MODELS_DIR, 'best_model.pkl')
keras_model_file = os.path.join(BASE_MODELS_DIR, 'keras_model.h5')
tflite_model_file = os.path.join(BASE_MODELS_DIR, 'converted_model.tflite')


def main():
    model = keras.models.load_model(best_model_file)

    tf.global_variables_initializer()
    keras.models.save_model(model, keras_model_file)

    converter = tf.lite.TFLiteConverter.from_keras_model_file(keras_model_file)
    tflite_model = converter.convert()

    with open(tflite_model_file, 'wb') as fout:
        fout.write(tflite_model)

    # cp model over to android dir
    # !cp converted_model.tflite ../AndroidApp/app/src/main/assets


if __name__ == '__main__':
    main()
