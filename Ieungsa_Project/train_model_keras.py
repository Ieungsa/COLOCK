import os
import pandas as pd
import numpy as np
import tensorflow as tf
from tensorflow.keras.layers import TextVectorization, Embedding, GlobalAveragePooling1D, Dense
from tensorflow.keras.models import Sequential
from sklearn.model_selection import train_test_split
import requests
import zipfile
import io

print("Starting Keras training and TFLite conversion script.")

# Step 1: Download and prepare the dataset
print("Downloading dataset...")
data_url = "https://archive.ics.uci.edu/ml/machine-learning-databases/00228/smsspamcollection.zip"
try:
    r = requests.get(data_url)
    r.raise_for_status()
    z = zipfile.ZipFile(io.BytesIO(r.content))
    data_dir = "data"
    if not os.path.exists(data_dir):
        os.makedirs(data_dir)
    z.extractall(data_dir)
    data_file = os.path.join(data_dir, "SMSSpamCollection")
    print("Dataset downloaded and extracted successfully.")
except Exception as e:
    print(f"Error during data download/extraction: {e}")
    exit(1)

# Step 2: Load data and map labels
df = pd.read_csv(data_file, sep='\t', header=None, names=['label', 'text'], on_bad_lines='skip')
df['label_int'] = df['label'].map({'ham': 0, 'spam': 1})
print("\nDataset sample:")
print(df.head())

# Step 3: Split data
X_train, X_test, y_train, y_test = train_test_split(
    df['text'],
    df['label_int'],
    test_size=0.2,
    random_state=42,
    stratify=df['label_int']
)

print(f"\nTraining set size: {len(X_train)}")
print(f"Testing set size: {len(X_test)}")

# Step 4: Create a TextVectorization layer
VOCAB_SIZE = 10000
SEQUENCE_LENGTH = 120 # Shorter sequence length for mobile
vectorize_layer = TextVectorization(
    max_tokens=VOCAB_SIZE,
    output_mode='int',
    output_sequence_length=SEQUENCE_LENGTH
)

# Adapt the layer to the training data vocabulary
print("\nAdapting vocabulary...")
vectorize_layer.adapt(X_train.to_numpy())

# Step 5: Build the Keras model
embedding_dim = 16
model = Sequential([
    vectorize_layer,
    Embedding(input_dim=VOCAB_SIZE + 1, output_dim=embedding_dim, name="embedding"),
    GlobalAveragePooling1D(),
    Dense(16, activation='relu'),
    Dense(1, activation='sigmoid')
])

# Step 6: Compile and train the model
print("\nCompiling and training model...")
model.compile(optimizer='adam',
              loss='binary_crossentropy',
              metrics=['accuracy'])

model.fit(
    X_train.to_numpy(),
    y_train.to_numpy(),
    epochs=5,
    validation_data=(X_test.to_numpy(), y_test.to_numpy())
)

# Step 7: Convert the model to TensorFlow Lite
print("\nConverting model to TensorFlow Lite...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
# Enable TF Select ops to handle the TextVectorization layer
converter.target_spec.supported_ops = [
  tf.lite.OpsSet.TFLITE_BUILTINS, # Enable TFLite ops.
  tf.lite.OpsSet.SELECT_TF_OPS # Enable TensorFlow ops.
]
tflite_model = converter.convert()

# Step 8: Save the TFLite model
output_dir = "keras_model"
if not os.path.exists(output_dir):
    os.makedirs(output_dir)

tflite_model_path = os.path.join(output_dir, "spam_model.tflite")
with open(tflite_model_path, 'wb') as f:
    f.write(tflite_model)

print(f"\nSuccessfully saved TFLite model to {tflite_model_path}")
print("Script finished.")
