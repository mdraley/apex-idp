import { useCallback, useState } from 'react'
import { useDropzone } from 'react-dropzone'
import { Upload, File, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useToast } from '@/components/ui/use-toast'
import { api } from '@/lib/api'

export default function UploadDropzone() {
    const [files, setFiles] = useState<File[]>([])
    const [uploading, setUploading] = useState(false)
    const { toast } = useToast()

    const onDrop = useCallback((acceptedFiles: File[]) => {
        setFiles(prev => [...prev, ...acceptedFiles])
    }, [])

    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop,
        accept: {
            'application/pdf': ['.pdf'],
            'image/jpeg': ['.jpg', '.jpeg'],
            'image/png': ['.png'],
            'image/tiff': ['.tif', '.tiff'],
        },
        multiple: true,
    })

    const removeFile = (index: number) => {
        setFiles(prev => prev.filter((_, i) => i !== index))
    }

    const handleUpload = async () => {
        if (files.length === 0) return

        setUploading(true)
        const formData = new FormData()
        files.forEach(file => {
            formData.append('files', file)
        })

        try {
            const response = await api.post('/batches', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
            })

            toast({
                title: 'Upload successful',
                description: `Batch ${response.data.id} created with ${files.length} files`,
            })

            setFiles([])
        } catch (error) {
            toast({
                title: 'Upload failed',
                description: 'Please try again',
                variant: 'destructive',
            })
        } finally {
            setUploading(false)
        }
    }

    return (
        <div className="space-y-4">
            <div
                {...getRootProps()}
                className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
                    isDragActive ? 'border-primary bg-primary/5' : 'border-gray-300 hover:border-gray-400'
                }`}
            >
                <input {...getInputProps()} />
                <Upload className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                {isDragActive ? (
                    <p className="text-lg">Drop the files here...</p>
                ) : (
                    <>
                        <p className="text-lg mb-2">Drag & drop files here, or click to select</p>
                        <p className="text-sm text-muted-foreground">
                            Supports PDF, JPG, PNG, TIFF formats
                        </p>
                    </>
                )}
            </div>

            {files.length > 0 && (
                <div className="space-y-2">
                    <h3 className="font-medium">Selected files ({files.length})</h3>
                    <div className="space-y-1 max-h-32 overflow-y-auto">
                        {files.map((file, index) => (
                            <div key={index} className="flex items-center justify-between p-2 bg-gray-50 rounded">
                                <div className="flex items-center space-x-2">
                                    <File className="h-4 w-4 text-gray-500" />
                                    <span className="text-sm truncate max-w-xs">{file.name}</span>
                                    <span className="text-xs text-muted-foreground">
                    ({(file.size / 1024 / 1024).toFixed(2)} MB)
                  </span>
                                </div>
                                <button
                                    onClick={() => removeFile(index)}
                                    className="text-gray-500 hover:text-red-500"
                                >
                                    <X className="h-4 w-4" />
                                </button>
                            </div>
                        ))}
                    </div>
                    <Button
                        onClick={handleUpload}
                        disabled={uploading}
                        className="w-full"
                    >
                        {uploading ? 'Uploading...' : `Upload ${files.length} files`}
                    </Button>
                </div>
            )}
        </div>
    )
}
