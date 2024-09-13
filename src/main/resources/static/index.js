// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

"use strict";

async function init() {
  // Get URL query params
  const params = new Proxy(new URLSearchParams(window.location.search), {
    get: (searchParams, prop) => searchParams.get(prop),
  });

  const error = params.error;
  if (error) {
    document.getElementById('result_message').textContent = `Error: ${error}`;
  } else {
    const result = params.result;
    if (result === 'save_success') {
      document.getElementById('result_message').textContent = 'Saved successfully';
    } else if (result === 'sync_success') {
      document.getElementById('result_message').textContent = 'Synced successfully';
    }
  }

  const type = params.type;
  if (!type) {
    document.getElementById("editor_form_loading").style.display = "none";
    return;
  }

  // Initialize the editor with a JSON schema
  const schemaResponse = await fetch(`schema/${type}.json`);
  if (!schemaResponse.ok) {
    throw new Error(`Could not fetch schema for ${type}`);
  }
  const schemaData = await schemaResponse.json();
  const editor = new JSONEditor(document.getElementById('editor_holder'), {
    disable_collapse: true,
    disable_edit_json: true,
    disable_properties: true,
    schema: schemaData,
    theme: 'bootstrap4',
    use_default_values: false,
  });

  const loadConfigResponse = await fetch(`config/${type}.json`);
  let etag = '';
  if (loadConfigResponse.ok) {
    const configData = await loadConfigResponse.json();
    editor.setValue(configData);
    etag = loadConfigResponse.headers.get('ETag');
    document.getElementById("editor_form_loading").style.display = "none";
    document.getElementById("editor_form").style.display = "block";
  }

  // Hook up the button to submit values
  document.getElementById('submit').addEventListener('click', async function () {
    document.getElementById("result_message").style.display = "none";
    document.getElementById("submit").style.display = "none";
    document.getElementById("submit_loading").style.display = "block";
    const editorData = editor.getValue();
    console.debug(editorData);
    const saveConfigResponse = await fetch(`config/${type}.json`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'ETag': etag,
      },
      body: JSON.stringify(editorData),
    });
    if (!saveConfigResponse.ok) {
      throw new Error(`Could not save configuration for ${type}`);
    }
    const saveConfigResponseData = await saveConfigResponse.json();
    console.debug(saveConfigResponseData);
    window.location.href = saveConfigResponseData['location'];
  });
}

document.addEventListener("DOMContentLoaded", function() { 
  fetch('/configs')
    .then(response => response.json())
    .then(data => {
      const listContainer = document.getElementById('dynamic-list');

      data.forEach(item => {
        const parts = item.split("/");
        let root = listContainer;

        parts.forEach((part, index) => {
          let existingElement = Array.from(root.children).find(child => child.textContent === part);

          if (!existingElement) {
            const li = document.createElement('li');

            if (index === parts.length - 1) {
              // File
              const a = document.createElement('a');
              a.href = "?type="+encodeURIComponent(item);
              a.textContent = part;
              li.appendChild(a);
              root.appendChild(li);
            } else {
              // Folder
              li.textContent = part;
              let ul = Array.from(li.children).find(child => child.tagName === 'UL');
              if (!ul) {
                ul = document.createElement('ul');
                li.appendChild(ul);
              }
              root.appendChild(li);
              root = ul;
            }
          } else {
            // Si l'élément existe déjà, on passe au niveau suivant
            let ul = Array.from(existingElement.children).find(child => child.tagName === 'UL');
            root = ul || existingElement;
          }
        });
      });
    })
    .catch(error => console.error('Error retrieving data:', error));
});


window.onload = init;
