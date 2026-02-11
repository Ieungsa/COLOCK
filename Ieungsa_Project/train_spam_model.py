import os
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
import re
import string

print("Starting TensorFlow/Keras training script.")

# Step 1: Load the dataset
print("Loading Korean vishing dataset...")
try:
    vishing_file = 'vishing_dataset_repo/Data_Collection_Preprocessing/df_data_vishing.csv'
    unharmful_file = 'vishing_dataset_repo/Data_Collection_Preprocessing/df_data_unharmful.csv'

    df_vishing = pd.read_csv(vishing_file)
    df_unharmful = pd.read_csv(unharmful_file)

    df_vishing['label'] = 1 # 1 for spam/vishing
    df_unharmful['label'] = 0 # 0 for ham/normal

    df = pd.concat([df_vishing, df_unharmful], ignore_index=True)
    df.dropna(subset=['transcript'], inplace=True)
    
    # Shuffle the dataset
    df = df.sample(frac=1, random_state=42).reset_index(drop=True)

    print("Dataset loaded successfully.")
except FileNotFoundError as e:
    print(f"Error: Dataset files not found. {e}")
    exit(1)

# Step 2: Split data
X = df['transcript']
y = df['label']
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

print(f"\nTraining set size: {len(X_train)}")
print(f"Testing set size: {len(X_test)}")

# Step 3: Create a TextVectorization layer
MAX_FEATURES = 10000  # Maximum number of words in the vocabulary
SEQUENCE_LENGTH = 250 # Max length of a sequence

# Custom standardization function to remove punctuation
def custom_standardization(input_data):
    lowercase = tf.strings.lower(input_data)
    stripped_html = tf.strings.regex_replace(lowercase, '<br />', ' ')
    return tf.strings.regex_replace(stripped_html,
                                  '[%s]' % re.escape(string.punctuation),
                                  '')

vectorize_layer = tf.keras.layers.TextVectorization(
    standardize=custom_standardization,
    max_tokens=MAX_FEATURES,
    output_mode='int',
    output_sequence_length=SEQUENCE_LENGTH)

# Adapt the layer to the training data
print("\nAdapting TextVectorization layer...")
vectorize_layer.adapt(X_train.values)

# Step 4: Build the Keras model
EMBEDDING_DIM = 16

model = tf.keras.Sequential([
    vectorize_layer,
    tf.keras.layers.Embedding(MAX_FEATURES, EMBEDDING_DIM),
    tf.keras.layers.GlobalAveragePooling1D(),
    tf.keras.layers.Dropout(0.5), # Dropout layer to prevent overfitting
    tf.keras.layers.Dense(1, activation='sigmoid')
])

# Step 5: Compile the model
model.compile(optimizer='adam',
              loss='binary_crossentropy',
              metrics=['accuracy'])

print("\nModel Summary:")
model.summary()

# Step 6: Train the model
print("\nTraining model...")
epochs = 10
history = model.fit(
    X_train,
    y_train,
    validation_data=(X_test, y_test),
    epochs=epochs)

# Step 7: Evaluate the model
print("\nEvaluating model...")
loss, accuracy = model.evaluate(X_test, y_test)
print(f"Model Accuracy: {accuracy:.4f}")

# Step 8: Save the Keras model
output_dir = "new_korean_model"
if not os.path.exists(output_dir):
    os.makedirs(output_dir)
model.save(os.path.join(output_dir, "spam_model.keras"))
print(f"\nKeras model saved to {output_dir}/spam_model.keras")

# Step 9: Convert the model to TensorFlow Lite
print("\nConverting model to TFLite...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,  # Enable TFLite ops.
    tf.lite.OpsSet.SELECT_TF_OPS  # Enable TensorFlow ops.
]
tflite_model = converter.convert()

# Save the TFLite model
tflite_output_path = os.path.join(output_dir, "spam_model.tflite")
with open(tflite_output_path, 'wb') as f:
    f.write(tflite_model)

print(f"TFLite model saved to {tflite_output_path}")
print("Keras training and TFLite conversion finished successfully.")