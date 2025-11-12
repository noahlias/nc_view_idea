import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { ViewportGizmo } from "three-viewport-gizmo";
import { parseGCode } from "./parseGCode";

type IdeaBridge = {
  postMessage(message: unknown): void;
  addMessageListener(handler: (payload: unknown) => void): () => void;
};

declare global {
  interface Window {
    ideaBridge?: IdeaBridge;
    __NC_HTTP_ENDPOINT?: string;
    __NC_HTTP_TOKEN?: string;
  }
}

(() => {
  let ideaBridge: IdeaBridge | null = null;
  const bridgeLogQueue: string[] = [];

  const emitBridgeLog = (text: string) => {
    const message = `[bridge] ${text}`;
    console.debug(message);
    if (ideaBridge) {
      try {
        ideaBridge.postMessage({ type: "bridgeDebug", debugMessage: message });
      } catch (error) {
        console.error("Failed to post debug message", error);
      }
    } else {
      bridgeLogQueue.push(message);
    }
  };

  const flushBridgeLogQueue = () => {
    if (!ideaBridge) return;
    while (bridgeLogQueue.length) {
      const message = bridgeLogQueue.shift();
      if (!message) continue;
      try {
        ideaBridge.postMessage({ type: "bridgeDebug", debugMessage: message });
      } catch (error) {
        console.error("Failed to flush debug message", error);
        break;
      }
    }
  };

  const sleep = (ms: number) =>
    new Promise((resolve) => {
      setTimeout(resolve, ms);
    });

  const createHttpBridge = async (): Promise<IdeaBridge> => {
    const endpoint = window.__NC_HTTP_ENDPOINT;
    const token = window.__NC_HTTP_TOKEN;
    if (!endpoint || !token) {
      throw new Error("HTTP bridge configuration missing");
    }

    const listeners = new Set<(payload: unknown) => void>();
    let lastVersion = 0;

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      "X-Ncviewer-Token": token,
    };

    const notifyListeners = (payload: unknown) => {
      listeners.forEach((listener) => {
        try {
          listener(payload);
        } catch (error) {
          console.error("HTTP bridge listener error", error);
        }
      });
    };

    const pollLoop = async () => {
      while (true) {
        try {
          const response = await fetch(`${endpoint}/poll?after=${lastVersion}`, {
            method: "GET",
            headers,
            cache: "no-store",
          });
          if (response.status === 200) {
            const data = await response.json();
            lastVersion = data.version;
            let payload: unknown = data.message;
            if (typeof payload === "string") {
              try {
                payload = JSON.parse(payload);
              } catch (error) {
                console.warn("Failed to parse payload JSON", error);
              }
            }
            notifyListeners(payload);
            await sleep(10);
            continue;
          }
          await sleep(250);
        } catch (error) {
          console.error("HTTP bridge poll error", error);
          await sleep(1000);
        }
      }
    };

    pollLoop().catch((error) => console.error("HTTP bridge poll loop error", error));

    return {
      postMessage(message: unknown) {
        const payload = typeof message === "string" ? message : JSON.stringify(message);
        fetch(`${endpoint}/event`, {
          method: "POST",
          headers,
          body: payload,
        }).catch((error) => console.error("HTTP bridge post error", error));
      },
      addMessageListener(handler: (payload: unknown) => void) {
        listeners.add(handler);
        return () => listeners.delete(handler);
      },
    };
  };

  const bootstrapBridge = async (callback: () => void) => {
    try {
      ideaBridge = await createHttpBridge();
      window.ideaBridge = ideaBridge;
      flushBridgeLogQueue();
      emitBridgeLog("HTTP bridge connected");
      callback();
    } catch (error) {
      console.error("Failed to initialize HTTP bridge", error);
      await sleep(1000);
      bootstrapBridge(callback);
    }
  };

  const viewer = document.getElementById("viewer") as HTMLDivElement | null;
  if (!viewer) {
    throw new Error("Viewer element not found");
  }

  const scene = new THREE.Scene();
  scene.up.set(0, 0, 1);
  const segmentsGroup = new THREE.Group();
  scene.add(segmentsGroup);

  const startPointGeometry = new THREE.SphereGeometry(1, 32, 32);
  const startPointMaterial = new THREE.MeshBasicMaterial({ color: 0x39ff14 });
  const startPointMesh = new THREE.Mesh(startPointGeometry, startPointMaterial);
  startPointMesh.visible = false;
  scene.add(startPointMesh);

  const endPointGeometry = new THREE.SphereGeometry(1, 32, 32);
  const endPointMaterial = new THREE.MeshBasicMaterial({ color: 0xff0000 });
  const endPointMesh = new THREE.Mesh(endPointGeometry, endPointMaterial);
  endPointMesh.visible = false;
  scene.add(endPointMesh);

  const aspect = viewer.clientWidth / viewer.clientHeight;
  const f0 = 20000;
  const camera = new THREE.OrthographicCamera(
    (-f0 * aspect) / 2,
    (f0 * aspect) / 2,
    f0 / 2,
    -f0 / 2,
    0.1,
    2 * f0,
  );
  camera.up.set(0, 0, 1);

  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(viewer.clientWidth, viewer.clientHeight);
  viewer.appendChild(renderer.domElement);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.mouseButtons = {
    LEFT: THREE.MOUSE.PAN,
    MIDDLE: THREE.MOUSE.ROTATE,
    RIGHT: null,
  } as typeof controls.mouseButtons;
  controls.screenSpacePanning = true;
  controls.zoomToCursor = true;

  const gizmo = new ViewportGizmo(camera, renderer, {
    placement: "bottom-right",
  });
  gizmo.attachControls(controls);

  let movements: ReturnType<typeof parseGCode> = [];
  let lineSegmentsMesh: THREE.LineSegments | null = null;
  let size = 0;

  const registerBridgeListeners = () => {
    ideaBridge?.addMessageListener((eventData) => {
      let payload: any = eventData;
      if (typeof payload === "string") {
        try {
          payload = JSON.parse(payload);
        } catch (error) {
          console.error("Failed to parse incoming message", error, payload);
          emitBridgeLog("failed to parse incoming payload");
          return;
        }
      }

      if (!payload) {
        emitBridgeLog("incoming payload missing");
        return;
      }

      const { type, ncText, lineNumber, settings } = payload;
      emitBridgeLog(`incoming message type=${type}`);
      const excludeCodes = settings?.excludeCodes || [
        "G10",
        "G30",
        "G53",
        "G90",
      ];

      switch (type) {
        case "loadGCode":
          if (typeof ncText === "string") {
            emitBridgeLog(`loadGCode received length=${ncText.length}`);
            movements = parseGCode(ncText, 64, excludeCodes);
            processGCode(movements, true);
          }
          break;

        case "cursorPositionChanged":
          emitBridgeLog(`cursorPositionChanged ${lineNumber}`);
          highlightLineInViewer(lineNumber);
          break;

        case "contentChanged":
          if (typeof ncText === "string") {
            emitBridgeLog(`contentChanged length=${ncText.length}`);
            movements = parseGCode(ncText, 64, excludeCodes);
            processGCode(movements, false);
          }
          break;
      }
    });
  };

  const raycaster = new THREE.Raycaster();
  const mouse = new THREE.Vector2();
  let isMouseDown = false;
  const mouseDownPosition = { x: 0, y: 0 };

  renderer.domElement.addEventListener("mousedown", (event) => {
    isMouseDown = true;
    mouseDownPosition.x = event.clientX;
    mouseDownPosition.y = event.clientY;
  });

  renderer.domElement.addEventListener("mouseup", (event) => {
    if (!isMouseDown) return;
    isMouseDown = false;

    const dx = event.clientX - mouseDownPosition.x;
    const dy = event.clientY - mouseDownPosition.y;
    const distanceSquared = dx * dx + dy * dy;
    const clickThreshold = 5 * 5;

    if (distanceSquared <= clickThreshold) {
      const rect = renderer.domElement.getBoundingClientRect();
      mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
      mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

      raycaster.params.Line.threshold = size / 100 / camera.zoom;
      raycaster.setFromCamera(mouse, camera);

      if (lineSegmentsMesh) {
        const intersects = raycaster.intersectObject(
          lineSegmentsMesh,
          false,
        );

        if (intersects.length > 0) {
          const intersect = intersects[0];
          const faceIndex = intersect.index ?? 0;
          const segmentIndex = Math.floor(faceIndex / 2);

          if (segmentIndex < movements.length - 1) {
            const movement = movements[segmentIndex + 1];
            const lineIdx = movement.lineNumber || 0;

            emitBridgeLog(`click highlight line=${lineIdx}`);
            ideaBridge?.postMessage({
              type: "highlightLine",
              lineNumber: lineIdx,
            });

            highlightLineSegmentsForLine(lineIdx);
            slider.value = String(segmentIndex);
          }
        } else {
          slider.value = "0";
          selectLineSegments(movements.length, movements.length);
        }
      }
    }
  });

  function highlightLineInViewer(lineNumber: number) {
    let startIdx = -1;
    let endIdx = -1;

    for (let i = 1; i < movements.length; i++) {
      if (movements[i].lineNumber === lineNumber) {
        const segmentIdx = i - 1;
        if (startIdx === -1) startIdx = segmentIdx;
        endIdx = segmentIdx;
      }
    }

    if (startIdx !== -1 && endIdx !== -1) {
      const maxValue = parseInt(slider.max, 10);
      slider.value = String(Math.min(endIdx, Number.isNaN(maxValue) ? endIdx : maxValue));
      selectLineSegments(startIdx, endIdx);
    }
  }

  function highlightLineSegmentsForLine(lineNumber: number) {
    let startIdx = -1;
    let endIdx = -1;

    for (let i = 1; i < movements.length; i++) {
      if (movements[i].lineNumber === lineNumber) {
        const segmentIdx = i - 1;
        if (startIdx === -1) startIdx = segmentIdx;
        endIdx = segmentIdx;
      }
    }

    if (startIdx !== -1 && endIdx !== -1) {
      selectLineSegments(startIdx, endIdx);
    }
  }

  function processGCode(parsedMovements: typeof movements, isInitialLoad = true) {
    clearSceneLines();

    movements = parsedMovements;
    emitBridgeLog(`Parsed movements: ${movements.length}`);

    const positions: number[] = [];
    const colorsArray: number[] = [];

    const colorFeed = new THREE.Color(colors.feedColor);
    const colorRapid = new THREE.Color(colors.rapidColor);

    for (let i = 1; i < movements.length; i++) {
      const start = new THREE.Vector3(
        movements[i - 1].X,
        movements[i - 1].Y,
        movements[i - 1].Z,
      );
      const end = new THREE.Vector3(
        movements[i].X,
        movements[i].Y,
        movements[i].Z,
      );
      const isRapid = movements[i].command === "G0";
      const color = isRapid ? colorRapid : colorFeed;

      positions.push(start.x, start.y, start.z, end.x, end.y, end.z);
      colorsArray.push(
        color.r,
        color.g,
        color.b,
        color.r,
        color.g,
        color.b,
      );
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute(
      "position",
      new THREE.Float32BufferAttribute(positions, 3),
    );
    geometry.setAttribute(
      "color",
      new THREE.Float32BufferAttribute(colorsArray, 3),
    );

    const material = new THREE.LineBasicMaterial({ vertexColors: true });
    lineSegmentsMesh = new THREE.LineSegments(geometry, material);
    segmentsGroup.add(lineSegmentsMesh);

    const segmentCount = Math.max(0, movements.length - 1);
    slider.disabled = segmentCount === 0;
    slider.value = "0";
    slider.max = segmentCount === 0 ? "0" : String(segmentCount - 1);

    if (segmentCount > 0) {
      selectLineSegments(0, 0);
    } else {
      clearSelection();
    }

    if (isInitialLoad) {
      resetCamera();
    }
  }

  const slider = document.getElementById("viewerSlider") as HTMLInputElement;

  const registerSliderHandler = () => {
    slider.addEventListener("input", (event) => {
      const value = parseInt((event.target as HTMLInputElement).value, 10);
      const segmentCount = Math.max(0, movements.length - 1);

      if (Number.isNaN(value) || value < 0 || value > segmentCount - 1) {
        return;
      }

      selectLineSegments(value, value);

      const movement = movements[value + 1];
      if (movement && movement.lineNumber !== undefined) {
        emitBridgeLog(`slider highlight line=${movement.lineNumber}`);
        ideaBridge?.postMessage({
          type: "highlightLine",
          lineNumber: movement.lineNumber,
        });
      }
    });
  };

  function addAxisLines(box: THREE.Box3) {
    const marginFactor = 0.2;
    const min = box.min;
    const max = box.max;

    const axes = [
      { dir: "x", color: 0xff3653, start: min.x, end: max.x },
      { dir: "y", color: 0x8adb00, start: min.y, end: max.y },
      { dir: "z", color: 0x2c8fff, start: min.z, end: max.z },
    ] as const;

    axes.forEach(({ dir, color, start, end }) => {
      const length = end - start;
      const margin = length * marginFactor;

      const from = new THREE.Vector3();
      const to = new THREE.Vector3();

      if (dir === "x") {
        from.set(start - margin, 0, 0);
        to.set(end + margin, 0, 0);
      } else if (dir === "y") {
        from.set(0, start - margin, 0);
        to.set(0, end + margin, 0);
      } else {
        from.set(0, 0, start - margin);
        to.set(0, 0, end + margin);
      }

      const geometry = new THREE.BufferGeometry().setFromPoints([from, to]);
      const material = new THREE.LineBasicMaterial({ color });
      const axisLine = new THREE.Line(geometry, material);
      axisLine.userData.isAxisLine = true;
      scene.add(axisLine);
    });
  }

  function clearSceneLines() {
    if (lineSegmentsMesh) {
      segmentsGroup.remove(lineSegmentsMesh);
      lineSegmentsMesh.geometry.dispose();
      lineSegmentsMesh.material.dispose();
      lineSegmentsMesh = null;
    }
    startPointMesh.visible = false;
    endPointMesh.visible = false;

    document.getElementById("posX")!.textContent = "0.000";
    document.getElementById("posY")!.textContent = "0.000";
    document.getElementById("posZ")!.textContent = "0.000";
  }

  const COLOR_THEMES = {
    dark: {
      feed: 0x50fa7b,
      rapid: 0xff5555,
      selected: 0xff79c6,
      afterSelected: 0x6272a4,
      background: 0x282a36,
    },
    light: {
      feed: 0x2f9e44,
      rapid: 0xd7263d,
      selected: 0x5f3dc4,
      afterSelected: 0x94a2b8,
      background: 0xf5f7fb,
    },
  } as const;

  let colors = {
    selectedColor: COLOR_THEMES.dark.selected,
    feedColor: COLOR_THEMES.dark.feed,
    rapidColor: COLOR_THEMES.dark.rapid,
    afterSelColor: COLOR_THEMES.dark.afterSelected,
  };

  function getActivePalette() {
    const theme = document.documentElement.dataset.theme === "light" ? "light" : "dark";
    return COLOR_THEMES[theme];
  }

  function animate() {
    requestAnimationFrame(animate);
    controls.update();
    if (startPointMesh.visible) {
      const pointSize = size / 200 / camera.zoom;
      startPointMesh.scale.set(pointSize, pointSize, pointSize);
      endPointMesh.scale.set(pointSize, pointSize, pointSize);
    }
    renderer.render(scene, camera);
    gizmo.render();
  }

  window.addEventListener("resize", function () {
    const viewerWidth = viewer.clientWidth;
    const viewerHeight = viewer.clientHeight;
    const newAspect = viewerWidth / viewerHeight;

    const viewSize = camera.top - camera.bottom;
    camera.left = (-viewSize * newAspect) / 2;
    camera.right = (viewSize * newAspect) / 2;
    camera.updateProjectionMatrix();

    renderer.setSize(viewerWidth, viewerHeight);
    gizmo.update();
  });

  function update3DViewColors() {
    const palette = getActivePalette();
    colors.feedColor = palette.feed;
    colors.rapidColor = palette.rapid;
    colors.selectedColor = palette.selected;
    colors.afterSelColor = palette.afterSelected;

    startPointMaterial.color.setHex(colors.feedColor);
    endPointMaterial.color.setHex(colors.rapidColor);

    renderer.setClearColor(palette.background);

    if (lineSegmentsMesh) {
      selectLineSegments(parseInt(slider.value, 10), parseInt(slider.value, 10));
    }

    const axisLines: THREE.Object3D[] = [];
    scene.traverse((child) => {
      if (child.userData.isAxisLine) axisLines.push(child);
    });
    axisLines.forEach((line) => scene.remove(line));
    let box = new THREE.Box3().setFromObject(segmentsGroup);
    if (box.isEmpty()) box = new THREE.Box3(new THREE.Vector3(-1, -1, -1), new THREE.Vector3(1, 1, 1));
    addAxisLines(box);
  }

  const observer = new MutationObserver((mutationsList) => {
    for (const mutation of mutationsList) {
      if (mutation.type === "attributes") {
        update3DViewColors();
        break;
      }
    }
  });
  observer.observe(document.body, { attributes: true });
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

  function initializeViewer() {
    emitBridgeLog("initializeViewer invoked");
    update3DViewColors();
    resetCamera();
    if (ideaBridge) {
      ideaBridge.postMessage({ type: "webviewReady" });
      emitBridgeLog("webviewReady posted");
    } else {
      emitBridgeLog("ideaBridge missing during initializeViewer");
    }
    animate();
  }

  const startOnceBridgeReady = () => {
    const start = () => {
      registerBridgeListeners();
      registerSliderHandler();
      initializeViewer();
    };

    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", start, { once: true });
    } else {
      start();
    }
  };

  bootstrapBridge(startOnceBridgeReady);

  function resetCamera() {
    let box = new THREE.Box3().setFromObject(segmentsGroup);

    if (box.isEmpty()) {
      box = new THREE.Box3(
        new THREE.Vector3(-1, -1, -1),
        new THREE.Vector3(1, 1, 1),
      );
    }

    size = box.getSize(new THREE.Vector3()).length();
    const center = box.getCenter(new THREE.Vector3());

    const f = size * 1.1;
    camera.left = (-f * aspect) / 2;
    camera.right = (f * aspect) / 2;
    camera.top = f / 2;
    camera.bottom = -f / 2;
    camera.zoom = 1;
    camera.updateProjectionMatrix();

    camera.position.set(
      center.x + size * 1.1,
      center.y + size * -1.1,
      size * 1.1,
    );
    camera.lookAt(center);

    controls.target.copy(center);
    controls.update();

    addAxisLines(box);
  }

  function clearSelection() {
    startPointMesh.visible = false;
    endPointMesh.visible = false;
    document.getElementById("posX")!.textContent = "0.000";
    document.getElementById("posY")!.textContent = "0.000";
    document.getElementById("posZ")!.textContent = "0.000";
  }

  function selectLineSegments(startIdx: number, endIdx: number) {
    if (!lineSegmentsMesh || movements.length < 2) {
      clearSelection();
      return;
    }

    const maxSegmentIndex = movements.length - 2;
    const clampedStart = Math.max(0, Math.min(startIdx, maxSegmentIndex));
    const clampedEnd = Math.max(clampedStart, Math.min(endIdx, maxSegmentIndex));

    const colorsAttr = lineSegmentsMesh.geometry.getAttribute("color") as THREE.BufferAttribute;
    const color = new THREE.Color();

    const baseColor = colors.feedColor;
    const selectedColor = colors.selectedColor;
    const afterSelectedColor = colors.afterSelColor;

    for (let i = 0; i < colorsAttr.count; i += 2) {
      const segmentIndex = i / 2;
      if (segmentIndex >= clampedStart && segmentIndex <= clampedEnd) {
        color.setHex(selectedColor);
      } else if (segmentIndex > clampedEnd) {
        color.setHex(afterSelectedColor);
      } else {
        color.setHex(baseColor);
      }

      const vi = segmentIndex * 2;
      colorsAttr.setXYZ(vi, color.r, color.g, color.b);
      colorsAttr.setXYZ(vi + 1, color.r, color.g, color.b);
    }

    colorsAttr.needsUpdate = true;

    if (clampedStart < movements.length - 1) {
      const startPos = new THREE.Vector3(
        movements[clampedStart].X,
        movements[clampedStart].Y,
        movements[clampedStart].Z,
      );
      const endPos = new THREE.Vector3(
        movements[clampedEnd + 1].X,
        movements[clampedEnd + 1].Y,
        movements[clampedEnd + 1].Z,
      );

      startPointMesh.position.copy(startPos);
      endPointMesh.position.copy(endPos);

      startPointMesh.visible = true;
      endPointMesh.visible = true;

      document.getElementById("posX")!.textContent = movements[clampedEnd + 1].X.toFixed(3);
      document.getElementById("posY")!.textContent = movements[clampedEnd + 1].Y.toFixed(3);
      document.getElementById("posZ")!.textContent = movements[clampedEnd + 1].Z.toFixed(3);
    } else {
      clearSelection();
    }
  }
})();
