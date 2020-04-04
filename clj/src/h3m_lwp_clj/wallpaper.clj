(ns h3m-lwp-clj.wallpaper
  (:import
   [com.badlogic.gdx.graphics OrthographicCamera]
   [com.badlogic.gdx.utils.viewport Viewport])
  (:require
   [h3m-lwp-clj.terrain :as terrain]
   [h3m-lwp-clj.objects :as objects]
   [h3m-lwp-clj.orth-camera :as orth-camera]
   [h3m-lwp-clj.input-processor :as input-processor]))


(defn create-renderer
  [settings-atom ^Viewport viewport h3m-map]
  (let [{position-update-interval :position-update-interval} @settings-atom
        camera ^OrthographicCamera (.getCamera viewport)
        camera-controller (input-processor/create camera (:size h3m-map))
        terrain-renderer (terrain/create-renderer h3m-map)
        objects-renderer (objects/create-renderer h3m-map)]
    (orth-camera/set-camera-updation-timer camera (:size h3m-map) position-update-interval)
    (.setToOrtho camera true)
    (fn []
      (terrain-renderer camera)
      (objects-renderer camera)
      camera-controller)))